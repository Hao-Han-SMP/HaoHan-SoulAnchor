package vn.haohan.soulanchor;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

public class FlashMathSelfCheckTest {

    @Test
    void testSnappyFlashEnvelopeAndScaleBounds() {
        int flashFrameTotal = 7;
        Set<Integer> visitedModels = new HashSet<>();

        for (int frame = 0; frame < flashFrameTotal; frame++) {
            double t = flashFrameTotal <= 1 ? 1D : frame / (double) (flashFrameTotal - 1);
            // Snappy lightning burst: quick rise to peak whiteout at t~0.18, smooth fast decay
            double envelope = t < 0.18D ? (t / 0.18D) : Math.pow(Math.max(0D, 1.0D - (t - 0.18D) / 0.82D), 1.3D);
            float scale = (float) (8D + 42D * envelope);

            assertTrue(envelope >= 0.0D && envelope <= 1.0D, "Envelope must stay in [0, 1]");
            assertTrue(scale >= 8.0F && scale <= 50.0F, "Scale must stay in [8, 50]");

            int alphaModel = envelope <= 0.02D ? 910000
                    : envelope < 0.25D ? 910001
                    : envelope < 0.50D ? 910002
                    : envelope < 0.75D ? 910003
                    : envelope < 0.90D ? 910004 : 910005;

            visitedModels.add(alphaModel);
            assertTrue(alphaModel >= 910000 && alphaModel <= 910005);
            if (frame == 0 || frame == flashFrameTotal - 1) {
                assertEquals(910000, alphaModel, "Boundary frames must resolve to baseline 910000");
                assertTrue(envelope <= 0.02D, "Boundary frames must be invisible");
            }
        }

        // Verify that all 6 alpha stages (0 through 5) are traversed smoothly without gaps
        for (int m = 910000; m <= 910005; m++) {
            assertTrue(visitedModels.contains(m), "Snappy flash must visit alpha model " + m);
        }
    }
}
