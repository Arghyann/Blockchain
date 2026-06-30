import java.math.BigInteger;

public class TestECC {

    public static void main(String[] args) {
        System.out.println("Running ECC Math standalone tests...");
        boolean allPassed = true;

        try {
            // Test 1: Verify G is on the curve
            BigInteger lhs = ECC.G.y.pow(2).mod(ECC.P);
            BigInteger rhs = ECC.G.x.pow(3).add(ECC.A.multiply(ECC.G.x)).add(ECC.B).mod(ECC.P);
            if (!lhs.equals(rhs)) {
                System.err.println("❌ Test 1 Failed: Base Point G is not on the curve!");
                allPassed = false;
            } else {
                System.out.println("✅ Test 1 Passed: Base Point G is on the curve.");
            }
        } catch (Exception e) {
            System.err.println("❌ Test 1 Failed with Exception: " + e.getMessage());
            allPassed = false;
        }

        try {
            // Test 2: Point Doubling vs Scalar Multiplier
            ECC.Point twoGAdd = ECC.add(ECC.G, ECC.G);
            ECC.Point twoGMult = ECC.multiply(ECC.G, BigInteger.valueOf(2));
            if (twoGAdd.isInfinity() || twoGMult.isInfinity()) {
                System.err.println("❌ Test 2 Failed: Addition or multiplication returned INFINITY incorrectly!");
                allPassed = false;
            } else if (!twoGAdd.equals(twoGMult)) {
                System.err.println("❌ Test 2 Failed: G + G (" + twoGAdd + ") does not equal 2 * G (" + twoGMult + ")!");
                allPassed = false;
            } else {
                System.out.println("✅ Test 2 Passed: G + G matches 2 * G.");
            }
        } catch (Exception e) {
            System.err.println("❌ Test 2 Failed with Exception: " + e.getMessage());
            e.printStackTrace();
            allPassed = false;
        }

        try {
            // Test 3: Order of the curve (N * G == INFINITY)
            ECC.Point nG = ECC.multiply(ECC.G, ECC.N);
            if (!nG.isInfinity()) {
                System.err.println("❌ Test 3 Failed: N * G did not return Point.INFINITY! Result: " + nG);
                allPassed = false;
            } else {
                System.out.println("✅ Test 3 Passed: N * G equals Point.INFINITY.");
            }
        } catch (Exception e) {
            System.err.println("❌ Test 3 Failed with Exception: " + e.getMessage());
            allPassed = false;
        }

        try {
            // Test 4: Key Gen, Sign, and Verify
            ECC.KeyPair keys = ECC.generateKeyPair();
            byte[] message = "Alice -> Aryan, 2000".getBytes();
            ECC.Signature sig = ECC.sign(message, keys.privateKey);

            boolean verified = ECC.verify(message, sig, keys.publicKey);
            if (!verified) {
                System.err.println("❌ Test 4 Failed: Signature verification failed for a valid signature!");
                allPassed = false;
            } else {
                System.out.println("✅ Test 4 Passed: Signature verified successfully.");
            }

            // Test 5: Signature Corruption
            byte[] corruptedMessage = "Alice -> Aryan, 2001".getBytes();
            boolean verifiedCorrupt = ECC.verify(corruptedMessage, sig, keys.publicKey);
            if (verifiedCorrupt) {
                System.err.println("❌ Test 5 Failed: Verification succeeded for a corrupted message!");
                allPassed = false;
            } else {
                System.out.println("✅ Test 5 Passed: Verification correctly fails on a corrupted message.");
            }

        } catch (Exception e) {
            System.err.println("❌ Test 4/5 Failed with Exception: " + e.getMessage());
            e.printStackTrace();
            allPassed = false;
        }

        System.out.println("=================================================");
        if (allPassed) {
            System.out.println("🎉 ALL PHASE 1 TESTS PASSED! You have successfully implemented ECC math!");
        } else {
            System.out.println("⚠️ SOME TESTS FAILED. Please review your math implementation in ECC.java.");
        }
        System.out.println("=================================================");
    }
}
