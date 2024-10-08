package ee.carlrobert.codegpt.ui.textarea

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.jetbrains.rd.util.AtomicReference
import ee.carlrobert.codegpt.CodeGPTKeys
import ee.carlrobert.codegpt.ui.textarea.suggestion.SuggestionsPopupManager
import kotlinx.coroutines.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.text.StyleContext
import javax.swing.text.StyledDocument

class CustomTextPaneKeyAdapter(
    private val project: Project,
    private val textPane: CustomTextPane,
    private val highlightedTextRanges: MutableList<Pair<TextRange, Boolean>>,
    onWebSearchIncluded: () -> Unit
) : KeyAdapter() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val suggestionsPopupManager =
        SuggestionsPopupManager(project, textPane, onWebSearchIncluded)
    private val popupOpenedAtRange: AtomicReference<TextRange?> = AtomicReference(null)

    override fun keyReleased(e: KeyEvent) {
        if (textPane.text.isEmpty()) {
            // TODO: Remove only the files that were added via shortcuts
            project.service<FileSearchService>().removeFilesFromSession()
            project.putUserData(CodeGPTKeys.ADDED_DOCUMENTATION, null)
            highlightedTextRanges.clear()
            suggestionsPopupManager.hidePopup()
            return
        }
        if (e.keyCode == KeyEvent.VK_BACK_SPACE) {
            if (popupOpenedAtRange.get() == TextRange(
                    textPane.caretPosition,
                    textPane.caretPosition + 1
                )
            ) {
                suggestionsPopupManager.hidePopup()
                return
            }

            if (textPane.text.isNotEmpty() && textPane.text.last() == '@') {
                suggestionsPopupManager.reset()
            }
        }

        when (e.keyCode) {
            KeyEvent.VK_UP, KeyEvent.VK_DOWN -> {
                suggestionsPopupManager.requestFocus()
                suggestionsPopupManager.selectNext()
                e.consume()
            }

            else -> {
                if (suggestionsPopupManager.isPopupVisible()) {
                    updateSuggestions()
                }
            }
        }
    }

    override fun keyTyped(e: KeyEvent) {
        val popupVisible = suggestionsPopupManager.isPopupVisible()
        if (e.keyChar == '@' && !popupVisible) {
            suggestionsPopupManager.showPopup(textPane)
            popupOpenedAtRange.getAndSet(
                TextRange(
                    textPane.caretPosition,
                    textPane.caretPosition + 1
                )
            )
            return
        } else if (popupVisible) {
            updateSuggestions()
        }

        val doc = textPane.document as StyledDocument
        if (textPane.caretPosition >= 0) {
            doc.setCharacterAttributes(
                textPane.caretPosition,
                1,
                StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE),
                true
            )
        }
    }

    override fun keyPressed(e: KeyEvent) {
        if (e.keyChar == '\t') {
            suggestionsPopupManager.requestFocus()
            suggestionsPopupManager.selectNext()
            e.consume()
        }
    }

    private fun updateSuggestions() {
        scope.launch {
            withContext(Dispatchers.Main) {
                val text = textPane.text
                val lastAtIndex = text.lastIndexOf('@')
                if (lastAtIndex != -1) {
                    val lastAtSearchIndex = text.lastIndexOf(':')
                    if (lastAtSearchIndex != -1) {
                        val searchText = text.substring(lastAtSearchIndex + 1)
                        if (searchText.isNotEmpty()) {
                            launch {
                                suggestionsPopupManager.updateSuggestions(searchText)
                            }
                        }
                    }
                } else {
                    suggestionsPopupManager.hidePopup()
                }
            }
        }
    }
}