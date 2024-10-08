package ee.carlrobert.codegpt.ui.textarea.suggestion

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.vcsUtil.showAbove
import ee.carlrobert.codegpt.ui.textarea.CustomTextPane
import ee.carlrobert.codegpt.ui.textarea.suggestion.item.*
import kotlinx.coroutines.*
import java.awt.Dimension
import java.awt.Point
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class SuggestionsPopupManager(
    private val project: Project,
    private val textPane: CustomTextPane,
    onWebSearchIncluded: () -> Unit,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var selectedActionGroup: SuggestionGroupItem? = null
    private var popup: JBPopup? = null
    private var originalLocation: Point? = null
    private val listModel = DefaultListModel<SuggestionItem>().apply {
        addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) = adjustPopupSize()
            override fun intervalRemoved(e: ListDataEvent) {}
            override fun contentsChanged(e: ListDataEvent) {}
        })
    }
    private val list = SuggestionList(listModel, textPane) {
        handleSuggestionItemSelection(it)
    }
    private val defaultActions: MutableList<SuggestionItem> = mutableListOf(
        FileSuggestionGroupItem(project),
        FolderSuggestionGroupItem(project),
        PersonaSuggestionGroupItem(),
        DocumentationSuggestionGroupItem(),
        WebSearchActionItem(onWebSearchIncluded),
    )

    fun showPopup(component: JComponent) {
        popup = SuggestionsPopupBuilder()
            .setPreferableFocusComponent(component)
            .setOnCancel {
                originalLocation = null
                true
            }
            .build(list)
        popup?.showAbove(component)
        originalLocation = component.locationOnScreen
        reset(true)
        // TODO: Apply initial focus to the popup until a proper search mechanism is in place.
        requestFocus()
        selectNext()
    }

    fun hidePopup() {
        popup?.cancel()
    }

    fun isPopupVisible(): Boolean {
        return popup?.isVisible ?: false
    }

    fun requestFocus() {
        list.requestFocus()
    }

    fun selectNext() {
        list.selectNext()
    }

    suspend fun updateSuggestions(searchText: String? = null) {
        val suggestions = withContext(Dispatchers.Default) {
            selectedActionGroup?.getSuggestions(searchText) ?: emptyList()
        }

        withContext(Dispatchers.Main) {
            listModel.clear()
            listModel.addAll(suggestions)
            list.revalidate()
            list.repaint()
        }
    }

    fun reset(clearPrevious: Boolean = true) {
        if (clearPrevious) {
            listModel.clear()
        }
        listModel.addAll(defaultActions)
        popup?.content?.revalidate()
        popup?.content?.repaint()
    }

    private fun handleSuggestionItemSelection(item: SuggestionItem) {
        when (item) {
            is SuggestionActionItem -> {
                hidePopup()
                item.execute(project, textPane)
            }

            is SuggestionGroupItem -> {
                selectedActionGroup = item
                scope.launch {
                    updateSuggestions()
                }
                textPane.appendHighlightedText(item.groupPrefix, withWhitespace = false)
                textPane.requestFocus()
            }
        }
    }

    private fun adjustPopupSize() {
        val maxVisibleRows = 15
        val newRowCount = minOf(listModel.size(), maxVisibleRows)
        list.setVisibleRowCount(newRowCount)
        list.revalidate()
        list.repaint()

        popup?.size = Dimension(list.preferredSize.width, list.preferredSize.height + 32)

        originalLocation?.let { original ->
            val newY = original.y - list.preferredSize.height - 32
            popup?.setLocation(Point(original.x, maxOf(newY, 0)))
        }
    }
}
