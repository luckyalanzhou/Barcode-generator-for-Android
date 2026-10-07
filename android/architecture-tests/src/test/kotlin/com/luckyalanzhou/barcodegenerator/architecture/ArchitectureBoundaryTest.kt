package com.luckyalanzhou.barcodegenerator.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun `production package dependencies follow the architecture layers`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val domain = Layer("Domain", "com.luckyalanzhou.barcodegenerator.domain..")
            val data = Layer("Data", "com.luckyalanzhou.barcodegenerator.data..")
            val dependencyInjection = Layer("DependencyInjection", "com.luckyalanzhou.barcodegenerator.di..")
            val icons = Layer("Icons", "com.luckyalanzhou.barcodegenerator.icons..")
            val presentation = Layer("Presentation", "com.luckyalanzhou.barcodegenerator.presentation..")
            val ui = Layer("UI", "com.luckyalanzhou.barcodegenerator.ui..")

            domain.dependsOnNothing()
            listOf(data, dependencyInjection, icons, presentation, ui).include()
        }
    }

    @Test
    fun `UI cannot import data implementations`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val ui = Layer("UI", "com.luckyalanzhou.barcodegenerator.ui..")
            val data = Layer("Data", "com.luckyalanzhou.barcodegenerator.data..")
            ui.doesNotDependOn(data)
            data.include()
        }
    }

    @Test
    fun `presentation cannot depend on UI`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val presentation = Layer("Presentation", "com.luckyalanzhou.barcodegenerator.presentation..")
            val ui = Layer("UI", "com.luckyalanzhou.barcodegenerator.ui..")
            presentation.doesNotDependOn(ui)
            ui.include()
        }
    }

    @Test
    fun `history feature UI cannot depend on presentation or app composition`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val historyFeature = Layer("HistoryFeature", "com.luckyalanzhou.barcodegenerator.ui.feature.history..")
            val presentation = Layer("Presentation", "com.luckyalanzhou.barcodegenerator.presentation..")
            val appComposition = Layer("AppComposition", "com.luckyalanzhou.barcodegenerator.ui.app..")
            historyFeature.doesNotDependOn(presentation, appComposition)
            listOf(presentation, appComposition).include()
        }
    }

    @Test
    fun `shared UI animation specs cannot depend on app composition`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val uiAnimation = Layer("UIAnimation", "com.luckyalanzhou.barcodegenerator.ui.animation..")
            val appComposition = Layer("AppComposition", "com.luckyalanzhou.barcodegenerator.ui.app..")
            uiAnimation.doesNotDependOn(appComposition)
            appComposition.include()
        }
    }

    @Test
    fun `long press menu contracts cannot depend on app composition`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val menuContracts = Layer("MenuContracts", "com.luckyalanzhou.barcodegenerator.ui.component.menu..")
            val appComposition = Layer("AppComposition", "com.luckyalanzhou.barcodegenerator.ui.app..")
            menuContracts.doesNotDependOn(appComposition)
            appComposition.include()
        }
    }

    @Test
    fun `result preview UI cannot depend on presentation or app composition`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val resultPreview = Layer("ResultPreview", "com.luckyalanzhou.barcodegenerator.ui.feature.results.preview..")
            val presentation = Layer("Presentation", "com.luckyalanzhou.barcodegenerator.presentation..")
            val appComposition = Layer("AppComposition", "com.luckyalanzhou.barcodegenerator.ui.app..")
            resultPreview.doesNotDependOn(presentation, appComposition)
            listOf(presentation, appComposition).include()
        }
    }

    @Test
    fun `data cannot depend on presentation or UI`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val data = Layer("Data", "com.luckyalanzhou.barcodegenerator.data..")
            val presentation = Layer("Presentation", "com.luckyalanzhou.barcodegenerator.presentation..")
            val ui = Layer("UI", "com.luckyalanzhou.barcodegenerator.ui..")
            data.doesNotDependOn(presentation, ui)
            listOf(presentation, ui).include()
        }
    }
}
