package io.github.n_a_monterocarvajal.frankupdater.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationDestinationTest {
    @Test
    fun `initial navigation contains the four product sections`() {
        assertEquals(
            listOf("Actualizaciones", "Buscar", "Biblioteca", "Ajustes"),
            NavigationDestination.entries.map { it.label },
        )
    }

    @Test
    fun `navigation labels preserve product names`() {
        assertEquals(
            listOf("Actualizaciones", "Buscar", "Biblioteca", "Ajustes"),
            NavigationDestination.entries.map { it.navigationLabel },
        )
    }
}
