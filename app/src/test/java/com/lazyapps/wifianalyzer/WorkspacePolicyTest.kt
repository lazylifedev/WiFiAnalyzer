package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.domain.WorkspaceName
import com.lazyapps.wifianalyzer.domain.WorkspaceSelectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePolicyTest {
    @Test fun namesAreTrimmedNfkcAndCaseInsensitive() { assertEquals("default", WorkspaceName.normalized(" ＤＥＦＡＵＬＴ ")); assertEquals("東大阪本社", WorkspaceName.display(" 東大阪本社 ")) }
    @Test fun emptyListNeedsDefault() { assertTrue(WorkspaceSelectionPolicy.needsDefault(emptyList())); assertFalse(WorkspaceSelectionPolicy.needsDefault(listOf(1))) }
    @Test fun missingSelectionRecoversToFirst() { assertEquals(3L, WorkspaceSelectionPolicy.selected(listOf(3, 8), 99)); assertEquals(8L, WorkspaceSelectionPolicy.selected(listOf(3, 8), 8)) }
    @Test fun lastDeletionNeedsDefaultAndDefaultNameIsNotReserved() { assertTrue(WorkspaceSelectionPolicy.needsDefault(listOf<Long>().filterNot { it == 1L })); assertEquals("default", WorkspaceName.normalized("default")) }
}
