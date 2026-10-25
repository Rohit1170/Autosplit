package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiIdCheckTest {
    private fun s(v: String) = UpiIdCheck.check(v)

    @Test fun emailsAreRejected() {
        assertEquals(UpiIdCheck.Status.EMAIL, s("rohit@gmail.com"))
        assertEquals(UpiIdCheck.Status.EMAIL, s("rohit@gmail"))
        assertEquals(UpiIdCheck.Status.EMAIL, s("rohit@yahoo.in"))
        assertFalse(UpiIdCheck.canSave("rohit@gmail.com"))
    }

    @Test fun knownHandlesPass() {
        assertEquals(UpiIdCheck.Status.OK, s("rohit@okaxis"))
        assertEquals(UpiIdCheck.Status.OK, s("9876543210@ybl"))
        assertEquals(UpiIdCheck.Status.OK, s(" Rohit.S@OkHdfcBank "))
    }

    @Test fun typingIsIncomplete() {
        assertEquals(UpiIdCheck.Status.INCOMPLETE, s("rohit"))
        assertEquals(UpiIdCheck.Status.INCOMPLETE, s("rohit@"))
        assertEquals(UpiIdCheck.Status.INCOMPLETE, s("rohit@oka"))
    }

    @Test fun unknownHandleWarnsButSaves() {
        assertEquals(UpiIdCheck.Status.UNKNOWN_HANDLE, s("rohit@newbankx"))
        assertTrue(UpiIdCheck.canSave("rohit@newbankx"))
    }

    @Test fun badFormat() {
        assertEquals(UpiIdCheck.Status.BAD_FORMAT, s("ro hit@ybl"))
        assertEquals(UpiIdCheck.Status.BAD_FORMAT, s("a@b@ybl"))
        assertEquals(UpiIdCheck.Status.BAD_FORMAT, s("r@ybl"))
    }
}
