package io.github.hectorvent.floci.core.huawei;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HuaweiHkdfTest {

    @Test
    void derivesKeysUsedByOfficialJavaAndPythonSdkVectors() {
        assertEquals("1c5724913ed69bdda5e7a25d0beb7f2518aa55d96f97e0649301127d35b9ed31",
                HuaweiHkdf.deriveHexKey(
                        "AccessKey", "SecretKey", "20191115/region-id-1/service"));
        assertEquals("5f2c01cbdf35366c541e012b779258a826d7376e6ff5238d76d85000690e1b50",
                HuaweiHkdf.deriveHexKey(
                        "AccessKey", "SecretKey", "20200608/test-region-1/demo"));
    }
}
