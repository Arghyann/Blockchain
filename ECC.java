import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class ECC {

    // secp256k1 Curve Parameters: y^2 = x^3 + 7 (mod P)
    public static final BigInteger P = BigInteger.TWO.pow(256)
            .subtract(BigInteger.TWO.pow(32))
            .subtract(BigInteger.valueOf(977));

    public static final BigInteger A = BigInteger.ZERO;
    public static final BigInteger B = BigInteger.valueOf(7);

    // Base Point G coordinates
    public static final BigInteger Gx = new BigInteger("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798", 16);
    public static final BigInteger Gy = new BigInteger("483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8", 16);

    // Order of G (N)
    public static final BigInteger N = new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);

    public static final Point G = new Point(Gx, Gy);

    public static class Point {
        public final BigInteger x;
        public final BigInteger y;

        public static final Point INFINITY = new Point(null, null);

        public Point(BigInteger x, BigInteger y) {
            this.x = x;
            this.y = y;
        }

        public boolean isInfinity() {
            return this.x == null && this.y == null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Point point = (Point) o;
            if (this.isInfinity() && point.isInfinity()) return true;
            if (this.isInfinity() || point.isInfinity()) return false;
            return x.equals(point.x) && y.equals(point.y);
        }

        @Override
        public int hashCode() {
            if (isInfinity()) return 0;
            return x.hashCode() ^ y.hashCode();
        }

        @Override
        public String toString() {
            if (isInfinity()) return "Point(Infinity)";
            return "Point(x=" + x.toString(16) + ", y=" + y.toString(16) + ")";
        }
    }

    public static class KeyPair {
        public final BigInteger privateKey;
        public final Point publicKey;

        public KeyPair(BigInteger privateKey, Point publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }
    }

    public static class Signature {
        public final BigInteger r;
        public final BigInteger s;

        public Signature(BigInteger r, BigInteger s) {
            this.r = r;
            this.s = s;
        }

        @Override
        public String toString() {
            return "Signature(r=" + r.toString(16) + ", s=" + s.toString(16) + ")";
        }
    }

    /**
     * EXERCISE 1.1: Elliptic Curve Point Addition and Doubling
     * Computes p1 + p2 mod P.
     */
    public static Point add(Point p1, Point p2) {

        if(p1.isInfinity()) {
            return p2;
        }
        if(p2.isInfinity()) {
            return p1;
        }
        if (p1.x.equals(p2.x) &&
                p1.y.add(p2.y).mod(P).equals(BigInteger.ZERO)) {
            return Point.INFINITY;
        }
        BigInteger m;
        if(p1.x.equals(p2.x) && p1.y.equals(p2.y)) {
            BigInteger numerator = p1.x.pow(2)
                    .multiply(BigInteger.valueOf(3))
                    .mod(P);
            BigInteger denominator = p1.y
                    .multiply(BigInteger.valueOf(2))
                    .mod(P);
            if (denominator.equals(BigInteger.ZERO)){
                return Point.INFINITY;
                }
            m = numerator.multiply(denominator.modInverse(P)).mod(P);
        }
        else{
            BigInteger numerator = p2.y.subtract(p1.y).mod(P);
            BigInteger denominator = p2.x.subtract(p1.x).mod(P);
            if (denominator.equals(BigInteger.ZERO)) {
                return Point.INFINITY;
            }
            m = numerator.multiply(denominator.modInverse(P)).mod(P);


        }
        BigInteger x3 = m.pow(2)
                .subtract(p1.x)
                .subtract(p2.x)
                .mod(P);
        BigInteger y3 = m.multiply(p1.x.subtract(x3))
                .subtract(p1.y)
                .mod(P);
        return new Point(x3, y3);
    }

    /**
     * EXERCISE 1.2: Scalar Multiplication via Double-and-Add
     * Computes k * p mod P.
     */
    public static Point multiply(Point p, BigInteger k) {
        k = k.mod(N);

        if (k.equals(BigInteger.ZERO) || p.isInfinity()) {
            return Point.INFINITY;
        }
        Point val=Point.INFINITY;
        Point curr=p;
        for(int i = 0; i<k.bitLength();i++){
            if(k.testBit(i)){
                val=add(val,curr);
            }
            curr=add(curr,curr);
        }

        return val; // Placeholder
    }

    public static BigInteger hashMessage(byte[] message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(message);
            return new BigInteger(1, hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    public static KeyPair generateKeyPair() {
        SecureRandom random = new SecureRandom();
        BigInteger privateKey;
        do {
            privateKey = new BigInteger(256, random);
        } while (privateKey.compareTo(BigInteger.ONE) < 0 || privateKey.compareTo(N.subtract(BigInteger.ONE)) > 0);

        Point publicKey = multiply(G, privateKey);
        return new KeyPair(privateKey, publicKey);
    }

    public static Signature sign(byte[] message, BigInteger privateKey) {
        BigInteger z = hashMessage(message);
        SecureRandom random = new SecureRandom();

        while (true) {
            BigInteger k;
            do {
                k = new BigInteger(256, random);
            } while (k.compareTo(BigInteger.ONE) < 0 || k.compareTo(N.subtract(BigInteger.ONE)) > 0);

            Point R = multiply(G, k);
            if (R.isInfinity()) continue;

            BigInteger r = R.x.mod(N);
            if (r.equals(BigInteger.ZERO)) continue;

            try {
                BigInteger kInv = k.modInverse(N);
                BigInteger s = kInv.multiply(z.add(r.multiply(privateKey))).mod(N);
                if (s.equals(BigInteger.ZERO)) continue;

                return new Signature(r, s);
            } catch (ArithmeticException e) {
                // Occurs if k is not coprime to N
            }
        }
    }

    public static boolean verify(byte[] message, Signature signature, Point publicKey) {
        BigInteger r = signature.r;
        BigInteger s = signature.s;

        if (r.compareTo(BigInteger.ONE) < 0 || r.compareTo(N) >= 0) return false;
        if (s.compareTo(BigInteger.ONE) < 0 || s.compareTo(N) >= 0) return false;

        BigInteger z = hashMessage(message);

        try {
            BigInteger w = s.modInverse(N);
            BigInteger u1 = z.multiply(w).mod(N);
            BigInteger u2 = r.multiply(w).mod(N);

            Point RPrime = add(multiply(G, u1), multiply(publicKey, u2));
            if (RPrime.isInfinity()) return false;

            return RPrime.x.mod(N).equals(r);
        } catch (ArithmeticException e) {
            return false;
        }
    }
}
