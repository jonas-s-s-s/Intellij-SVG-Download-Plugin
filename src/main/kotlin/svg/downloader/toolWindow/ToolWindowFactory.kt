package svg.downloader.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
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
import svg.downloader.utils.svgToJpegByteArray
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
    private var currentPage = 1
    private var currentSearchTerm: String = ""
    private lateinit var directoryField: TextFieldWithBrowseButton
    private lateinit var searchButton: JButton
    private lateinit var statusLabel: JBLabel
    private lateinit var searchTextField: JBTextField
    private lateinit var pageLabel: JBLabel

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
            add(Box.createVerticalStrut(10))
            add(createPageControls())
            add(Box.createVerticalStrut(15))
        }

        statusLabel = createStatusLabel()

        // Results List
        val scrollPane = createResultsList()

        // Center panel combines inputs and results
        val centerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(inputPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.PAGE_END)
        }

        mainPanel.add(centerPanel, BorderLayout.CENTER)
        return mainPanel
    }

    // -------------------------
    // Components Section
    // -------------------------

    private fun createStatusLabel(): JBLabel = JBLabel().apply {
        foreground = JBColor.GRAY
        isVisible = false
    }

    private fun createTitleLabel(): JBLabel =
        JBLabel("SVG Icon Downloader").apply {
            font = font.deriveFont(Font.BOLD, TITLE_FONT_SIZE)
            border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
        }

    private fun createDirectoryPicker(): JPanel {
        directoryField = TextFieldWithBrowseButton().apply {
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
        searchTextField = JBTextField()
        searchButton = JButton("Search")

        searchButton.addActionListener {
            val searchTerm = searchTextField.text.trim()
            performSearch(searchTerm, 1)
        }

        return JBPanel<JBPanel<*>>(BorderLayout(5, 0)).apply {
            add(JBLabel("Search for SVG:"), BorderLayout.WEST)
            add(searchTextField, BorderLayout.CENTER)
            add(searchButton, BorderLayout.EAST)
        }
    }

    private fun createPageControls(): JPanel {
        val previousButton = JButton("Previous")
        val nextButton = JButton("Next")
        pageLabel = JBLabel("Page $currentPage")

        previousButton.addActionListener {
            if (currentPage > 1) {
                if (currentSearchTerm.isNotEmpty())
                    performSearch(currentSearchTerm, currentPage - 1)
            }
        }

        nextButton.addActionListener {
            if (currentSearchTerm.isNotEmpty())
                performSearch(currentSearchTerm, currentPage + 1)
        }

        return JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            add(previousButton)
            add(nextButton)
            add(pageLabel)
        }
    }

    private fun performSearch(searchTerm: String, page: Int) {
        if (searchTerm.isEmpty()) return

        currentSearchTerm = searchTerm
        currentPage = page
        pageLabel.text = "Page $currentPage"

        searchButton.isEnabled = false
        statusLabel.text = "Searching page $page..."
        statusLabel.isVisible = true

        val worker = object : SwingWorker<List<SvgItem>, Void>() {
            override fun doInBackground(): List<SvgItem> {
                val html = fetchSvgRepoPage(searchTerm, page)
                return extractSvgItems(html)
            }

            override fun done() {
                try {
                    listModel.clear()
                    get()?.forEach { listModel.addElement(it) }
                } catch (e: Exception) {
                    statusLabel.text = "Error: ${e.message}"
                } finally {
                    SwingUtilities.invokeLater {
                        searchButton.isEnabled = true
                        statusLabel.isVisible = false
                    }
                }
            }
        }
        worker.execute()
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
                            val i = listModel.getElementAt(index)
                            projectService.writeSvg(directoryField.text, i.fileName, i.svgContent)
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
            var jpegBytes = ByteArray(0)
            try {
                jpegBytes = svgToJpegByteArray(it.svgContent, SEARCH_ICON_WIDTH, SEARCH_ICON_HEIGHT)
            } catch (e: Exception) {
                logger<CustomListCellRenderer>().info("Failed to convert SVG to JPEG: ${e.message}")
            }

            text = "<html>${it.name}<br><small>${it.fileName}</small></html>"
            icon = ImageIcon(jpegBytes)
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

