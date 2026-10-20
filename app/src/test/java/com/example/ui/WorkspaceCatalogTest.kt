package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCatalogTest {
    @Test
    fun screenCatalogContainsScreensFiveThroughTwenty() {
        assertEquals((5..20).toList(), workspaceScreens.map { it.number })
        assertEquals(16, workspaceScreens.map { it.route }.distinct().size)
        val sharedRoutes = setOf("splash", "login", "account-recovery", "role-portal")
        assertEquals(20, (workspaceScreens.map { it.route }.toSet() + sharedRoutes).size)
    }

    @Test
    fun moduleCatalogContainsSixteenGuideModules() {
        assertEquals((1..16).toList(), workspaceModules.map { it.number })
        assertEquals(16, workspaceModules.size)

        val appRoutes = workspaceScreens.map { it.route }.toSet() + "role-portal"
        assertTrue(workspaceModules.all { it.route in appRoutes })
    }

    @Test
    fun eachRoleHasAnAuthorizedHomeScreen() {
        WorkspaceRole.values().forEach { role ->
            val home = workspaceScreens.firstOrNull { it.route == role.homeRoute }
            assertNotNull("${role.title} home route must exist", home)
            assertTrue("${role.title} must be allowed into its home route", role in home!!.roles)
        }
    }

    @Test
    fun serverRoleValuesMapToTheExpectedPortal() {
        assertEquals(WorkspaceRole.SUPER_ADMIN, WorkspaceRole.fromServerValue("administrator"))
        assertEquals(WorkspaceRole.HOD, WorkspaceRole.fromServerValue("HOD"))
        assertEquals(WorkspaceRole.TEACHER, WorkspaceRole.fromServerValue("faculty"))
        assertEquals(WorkspaceRole.STUDENT, WorkspaceRole.fromServerValue("student"))
        assertEquals(WorkspaceRole.GUARDIAN, WorkspaceRole.fromServerValue("parent"))
        assertEquals(null, WorkspaceRole.fromServerValue("unknown"))
    }
}
