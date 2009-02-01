package org.basex.query.func;

import static org.basex.query.QueryText.*;
import static org.basex.util.Token.*;
import java.util.regex.Pattern;
import org.basex.BaseX;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.expr.Expr;
import org.basex.query.item.Bln;
import org.basex.query.item.Item;
import org.basex.query.item.Itr;
import org.basex.query.item.Str;
import org.basex.query.iter.Iter;
import org.basex.query.iter.SeqIter;
import org.basex.query.util.Err;
import org.basex.util.Array;
import org.basex.util.TokenBuilder;
import org.basex.util.XMLToken;

/**
 * String functions.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
final class FNStr extends Fun {
  @Override
  public Iter iter(final QueryContext ctx, final Iter[] arg)
      throws QueryException {
    final Iter iter = arg[0];

    switch(func) {
      case CODESTR:
        return cp2str(iter);
      case STCODE:
        return str2cp(iter);
      case COMPARE:
        if(arg.length == 3) checkColl(arg[2]);
        Item it1 = iter.atomic();
        Item it2 = arg[1].atomic();
        if(it1 == null || it2 == null) return Iter.EMPTY;
        final int d = diff(checkStr(it1), checkStr(it2));
        return Itr.get(Math.max(-1, Math.min(1, d))).iter();
      case CODEPNT:
        it1 = iter.atomic();
        it2 = arg[1].atomic();
        if(it1 == null || it2 == null) return Iter.EMPTY;
        return Bln.get(eq(checkStr(it1), checkStr(it2))).iter();
      case STRJOIN:
        return strjoin(arg);
      case SUBSTR:
        return substr(arg);
      case NORMUNI:
        return normuni(arg);
      case UPPER:
        return Str.get(uc(checkStr(iter))).iter();
      case LOWER:
        return Str.get(lc(checkStr(iter))).iter();
      case TRANS:
        return trans(arg);
      case ENCURI:
        return Str.get(uri(checkStr(iter), false)).iter();
      case IRIURI:
        return Str.get(uri(checkStr(iter), true)).iter();
      case ESCURI:
        return Str.get(esc(checkStr(iter))).iter();
      case CONCAT:
        final TokenBuilder tb = new TokenBuilder();
        for(final Iter a : arg) {
          final Item it = a.atomic();
          if(it != null) tb.add(it.str());
        }
        return Str.get(tb.finish()).iter();
      case CONTAINS:
        if(arg.length == 3) checkColl(arg[2]);
        Item it = arg[1].atomic();
        if(it == null) return Bln.TRUE.iter();
        return Bln.get(contains(checkStr(iter), checkStr(it))).iter();
      case STARTS:
        if(arg.length == 3) checkColl(arg[2]);
        it = arg[1].atomic();
        if(it == null) return Bln.TRUE.iter();
        return Bln.get(startsWith(checkStr(iter), checkStr(it))).iter();
      case ENDS:
        if(arg.length == 3) checkColl(arg[2]);
        it = arg[1].atomic();
        if(it == null) return Bln.TRUE.iter();
        return Bln.get(endsWith(checkStr(iter), checkStr(it))).iter();
      case SUBAFTER:
        if(arg.length == 3) checkColl(arg[2]);
        final byte[] str = checkStr(iter);
        final byte[] sa = checkStr(arg[1]);
        int pa = indexOf(str, sa);
        return (pa != -1 ? Str.get(substring(str, pa + sa.length)) :
          Str.ZERO).iter();
      case SUBBEFORE:
        if(arg.length == 3) checkColl(arg[2]);
        final byte[] sb = checkStr(iter);
        final int pb = indexOf(sb, checkStr(arg[1]));
        return (pb > 0 ? Str.get(substring(sb, 0, pb)) : Str.ZERO).iter();
      default:
        BaseX.notexpected(func); return null;
    }
  }

  @Override
  public Expr c(final QueryContext ctx) throws QueryException {
    switch(func) {
      case CONTAINS:
        final byte[] i = args[1].i() ? checkStr((Item) args[1]) : null;
        // query string is empty; return true
        if(args[1].e() || i != null && i.length == 0) return Bln.TRUE;
        // input string is empty; return false
        if(args[0].e() && i != null && i.length != 0) return Bln.FALSE;
        return this;
      default:
        return this;
    }
  }

  /**
   * Converts codepoints to a string.
   * @param iter iterator
   * @return iterator
   * @throws QueryException query exception
   */
  private Iter cp2str(final Iter iter) throws QueryException {
    final TokenBuilder tb = new TokenBuilder();
    Item i;
    while((i = iter.next()) != null) {
      final int n = (int) checkItr(i);
      if(!XMLToken.valid(n)) Err.or(INVCODE, i, this);
      tb.addUTF(n);
    }
    return Str.get(tb.finish()).iter();
  }

  /**
   * Converts a string to codepoints.
   * @param iter iterator
   * @return iterator
   * @throws QueryException query exception
   */
  private Iter str2cp(final Iter iter) throws QueryException {
    final Item it = iter.atomic();
    if(it == null) return Iter.EMPTY;
    final byte[] s = checkStr(it);

    final SeqIter seq = new SeqIter();
    for(int l = 0; l < s.length; l += cl(s[l])) seq.add(Itr.get(cp(s, l)));
    return seq;
  }

  /**
   * Returns a substring.
   * @param arg arguments
   * @return iterator
   * @throws QueryException xquery exception
   */
  private Iter substr(final Iter[] arg) throws QueryException {
    // normalize positions
    final double ds = checkDbl(arg[1]);
    final byte[] str = checkStr(arg[0]);
    final boolean end = arg.length == 3;
    int l = len(str);
    if(ds != ds) return Str.ZERO.iter();
    int s = (int) Math.floor(ds - .5);
    int e = end ? (int) Math.floor(checkDbl(arg[2]) + .5) : l;
    if(s < 0) {
      e += s;
      s = 0;
    }
    e = Math.min(l, end ? s + e : Integer.MAX_VALUE);
    if(s >= e) return Str.ZERO.iter();
    if(ascii(str)) return Str.get(substring(str, s, e)).iter();
    if(s == 0 && e == str.length) return Str.get(str).iter();

    int ss = s;
    int ee = e;
    int p = 0;
    for(l = 0; l < str.length; l += cl(str[l]), p++) {
      if(p == s) ss = l;
      if(p == e) ee = l;
    }
    if(p == e) ee = l;
    return Str.get(Array.create(str, ss, ee - ss)).iter();
  }

  /**
   * Returns a translated string.
   * @param arg arguments
   * @return string
   * @throws QueryException xquery exception
   */
  private Iter trans(final Iter[] arg) throws QueryException {
    final byte[] str = checkStr(arg[0]);
    final Item is = arg[1].atomic();
    if(is == null) Err.empty(this);    
    final byte[] sea = checkStr(is);
    final Item ir = arg[2].atomic();
    if(ir == null) Err.empty(this);    
    final byte[] rep = checkStr(ir);
    return Str.get(ascii(str) && ascii(sea) && ascii(rep) ?
      translate(str, sea, rep) : token(Pattern.compile(string(sea),
      Pattern.LITERAL).matcher(string(str)).replaceAll(string(rep)))).iter();
  }

  /**
   * Returns a translated string.
   * @param arg arguments
   * @return iterator
   * @throws QueryException xquery exception
   */
  private Iter strjoin(final Iter[] arg) throws QueryException {
    final Item is = arg[1].atomic();
    if(is == null) Err.empty(this);    
    final byte[] sep = checkStr(is);
    final TokenBuilder tb = new TokenBuilder();
    final Iter iter = arg[0];
    int c = 0;
    Item i;
    while((i = iter.next()) != null) {
      tb.add(checkStr(i));
      tb.add(sep);
      c++;
    }
    final byte[] v = tb.finish();
    return Str.get(c == 0 ? v : substring(v, 0, v.length - sep.length)).iter();
  }

  /** Normalization types. */
  private enum Norm {
    /** C Normalization.      */ C("NFC"),
    /** D Normalization.      */ D("NFD"),
    /** KC Normalization.     */ KC("NFKC"),
    /** KD Normalization.     */ KD("NFKD"),
    /** Simple Normalization. */ S("");
    /** Name of Normalization.*/ byte[] name;

    /**
     * Constructor.
     * @param n name
     */
    Norm(final String n) {
      name = token(n);
    }
  }

  /**
   * Returns normalized unicode.
   * @param arg arguments
   * @return string
   * @throws QueryException xquery exception
   */
  private Iter normuni(final Iter[] arg) throws QueryException {
    final byte[] str = checkStr(arg[0]);
    Norm norm = null;
    if(arg.length == 2) {
      final byte[] n = uc(trim(checkStr(arg[1])));
      for(final Norm f : Norm.values()) if(eq(f.name, n)) norm = f;
      if(norm == null) Err.or(NORMUNI, n);
    } else {
      norm = Norm.C;
    }
    // [CG] XQuery/normalize-unicode
    return Str.get(str).iter();
  }

  /** Reserved characters. */
  //private static final byte[] RES = token("!#'*()-._~");
  private static final byte[] RES = token("-._~");
  /** Reserved characters. */
  private static final byte[] IRIRES = token("!#$%&*'()+,-./:;=?@[]~_");

  /**
   * Returns a URI encoded token.
   * @param tok token
   * @param iri input
   * @return encoded token
   */
  private static byte[] uri(final byte[] tok, final boolean iri) {
    final int tl = tok.length;
    final TokenBuilder tb = new TokenBuilder();
    for(int t = 0; t < tl; t++) {
      final byte b = tok[t];
      if(letterOrDigit(b) || !iri && contains(RES, b) ||
          iri && contains(IRIRES, b)) tb.add(b);
      else hex(tb, b);
    }
    return tb.finish();
  }

  /**
   * Escapes the specified token.
   * @param tok token
   * @return escaped token
   */
  public static byte[] esc(final byte[] tok) {
    final int tl = tok.length;
    final TokenBuilder tb = new TokenBuilder();
    for(int t = 0; t < tl; t++) {
      final byte b = tok[t];
      if(b >= 32 && b <= 126) tb.add(b);
      else hex(tb, b);
    }
    return tb.finish();
  }

  /** Hex codes. */
  private static final byte[] HEX = token("0123456789ABCDEF");

  /**
   * Adds the specified byte in hex code.
   * @param tb token builder
   * @param b byte to be added
   */
  private static void hex(final TokenBuilder tb, final byte b) {
    tb.add('%');
    tb.add(HEX[(b & 0xFF) >> 4]);
    tb.add(HEX[(b & 0xFF) & 15]);
  }
}
