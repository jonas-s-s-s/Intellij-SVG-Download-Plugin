package svg.downloader.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.ui.content.ContentFactory
import svg.downloader.services.ProjectService
import svg.downloader.utils.SvgItem
import svg.downloader.utils.extractSvgItems
import svg.downloader.utils.fetchSvgRepoPage
import svg.downloader.utils.svgToPngByteArray
import java.awt.*
import javax.swing.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

// -------------------------
// Constants
// -------------------------

private const val PANEL_PADDING = 10
private const val TITLE_FONT_SIZE = 16f
private const val SEARCH_ICON_WIDTH = 80f
private const val SEARCH_ICON_HEIGHT = 80f
private const val CELL_TEXT_GAP = 20
private const val CELL_PADDING = 15
private const val RESULTS_VISIBLE_ROWS = 5

// -------------------------
// ToolWindowFactory Impl
// -------------------------

class ToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowUI = ToolWindowUI(toolWindow)
        val content = ContentFactory.getInstance().createContent(toolWindowUI.createContentPanel(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

// -------------------------
// ToolWindow UI
// -------------------------

private class ToolWindowUI(private val toolWindow: ToolWindow) {
    private val project: Project = toolWindow.project
    private val projectService: ProjectService = project.service()
    private val listModel = DefaultListModel<SvgItem>()

    // Main panel creation function
    fun createContentPanel(): JPanel {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_PADDING)
        }

        // Title Section
        val titleLabel = createTitleLabel()
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        // Input & Search Section
        val directoryField = createDirectoryPicker()
        val searchPanel = createSearchSection(directoryField)

        val inputPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(directoryField)
            add(Box.createVerticalStrut(10))
            add(searchPanel)
            add(Box.createVerticalStrut(15))
        }

        // Results List
        val scrollPane = createResultsList()

        // Center panel combines inputs and results
        val centerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(inputPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        mainPanel.add(centerPanel, BorderLayout.CENTER)
        return mainPanel
    }

    // -------------------------
    // Components Section
    // -------------------------

    private fun createTitleLabel(): JBLabel =
        JBLabel("SVG Icon Downloader").apply {
            font = font.deriveFont(Font.BOLD, TITLE_FONT_SIZE)
            border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
        }

    private fun createDirectoryPicker(): JPanel {
        val directoryField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project)
            )
            text = projectService.getProjectRootPath().toString()
        }

        return JBPanel<JBPanel<*>>(BorderLayout(0, 5)).apply {
            add(JBLabel("Pick a download directory:"), BorderLayout.NORTH)
            add(directoryField, BorderLayout.CENTER)
        }
    }

    private fun createSearchSection(directoryPanel: JPanel): JPanel {
        val searchTextField = JBTextField()
        val searchButton = JButton("Search").apply {
            addActionListener {
                listModel.clear()
                val searchTerm = searchTextField.text.trim()
                if (searchTerm.isNotEmpty()) {
                    isEnabled = false
                    val html = fetchSvgRepoPage(searchTerm, 1)
                    val items = extractSvgItems(html)
                    items.forEach { listModel.addElement(it) }
                    isEnabled = true
                }
            }
        }

        return JBPanel<JBPanel<*>>(BorderLayout(5, 0)).apply {
            add(JBLabel("Searching for SVG with name:"), BorderLayout.WEST)
            add(searchTextField, BorderLayout.CENTER)
            add(searchButton, BorderLayout.EAST)
        }
    }

    private fun createResultsList(): JScrollPane {
        val resultsList = JBList(listModel).apply {
            visibleRowCount = RESULTS_VISIBLE_ROWS
            cellRenderer = CustomListCellRenderer()
            fixedCellHeight = -1
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 1) {
                        val index = locationToIndex(e.point)
                        if (index != -1) {
                            listModel.getElementAt(index)
                            // Item clicked — extend behavior here if needed
                        }
                    }
                }
            })
        }

        return JBScrollPane(resultsList).apply {
            border = BorderFactory.createLineBorder(JBColor.border())
        }
    }
}

// -------------------------
// Custom Cell Renderer
// -------------------------

private class CustomListCellRenderer : JBLabel(), ListCellRenderer<SvgItem> {
    override fun getListCellRendererComponent(
        list: JList<out SvgItem>, value: SvgItem?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): CustomListCellRenderer {
        value?.let {
            var pngBytes = ByteArray(0)
            try {
                pngBytes = svgToPngByteArray(it.svgContent, SEARCH_ICON_WIDTH, SEARCH_ICON_HEIGHT)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            text = it.name
            icon = ImageIcon(pngBytes)
            preferredSize = Dimension(
                (SEARCH_ICON_WIDTH + 200).toInt(), (SEARCH_ICON_HEIGHT + 20).toInt()
            )
        }

        border = BorderFactory.createEmptyBorder(10, CELL_PADDING, 10, CELL_PADDING)
        background = if (isSelected) JBColor.background().darker() else JBColor.background()
        foreground = if (isSelected) JBColor.foreground().brighter() else JBColor.foreground()
        isOpaque = true
        iconTextGap = CELL_TEXT_GAP

        return this
    }
}
