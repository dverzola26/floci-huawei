package io.github.hectorvent.floci.core.huawei;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuaweiRequestClassifierTest {

    private final HuaweiRequestClassifier classifier = new HuaweiRequestClassifier();

    @Test
    void classifiesEveryRecognizedHuaweiAlgorithm() {
        for (HuaweiAuthAlgorithm algorithm : HuaweiAuthAlgorithm.values()) {
            String authorization = algorithm.authorizationPrefix() + " example-signing-fields";
            assertEquals(algorithm, classifier.classify(authorization).orElseThrow());
        }
    }

    @Test
    void doesNotClassifyAwsOrMalformedAuthorization() {
        assertTrue(classifier.classify((String) null).isEmpty());
        assertTrue(classifier.classify("").isEmpty());
        assertTrue(classifier.classify("AWS4-HMAC-SHA256 Credential=test").isEmpty());
        assertTrue(classifier.classify("SDK-HMAC-SHA256").isEmpty());
        assertTrue(classifier.classify("SDK-HMAC-SHA256X example").isEmpty());
    }
}
