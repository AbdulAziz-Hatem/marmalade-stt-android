package app.marmalade.stt.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcRssTest {

    @Test
    fun `VmRSS in kB becomes bytes`() {
        assertEquals(123_456L * 1024, ProcRss.parseVmRssLine("VmRSS:\t  123456 kB"))
    }

    @Test
    fun `malformed lines read as unknown`() {
        assertNull(ProcRss.parseVmRssLine("VmRSS:"))
        assertNull(ProcRss.parseVmRssLine("VmRSS:\t12 MB"))
        assertNull(ProcRss.parseVmRssLine("VmRSS:\tlots kB"))
    }
}
