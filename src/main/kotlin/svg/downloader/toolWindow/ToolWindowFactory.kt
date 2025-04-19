package svg.downloader.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.content.ContentFactory
import svg.downloader.services.ProjectService
import svg.downloader.utils.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class ToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val ui = SvgDownloaderUI(toolWindow)
        val content = ContentFactory.getInstance().createContent(ui.createMainPanel(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

private class SvgDownloaderUI(private val toolWindow: ToolWindow) {
    private val project: Project = toolWindow.project
    private val projectService: ProjectService = project.service()
    private val svgListModel = DefaultListModel<SvgItem>()

    fun createMainPanel(): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(createTitleLabel(), BorderLayout.NORTH)
            add(createCenterPanel(), BorderLayout.CENTER)
        }
    }

    private fun createTitleLabel(): JBLabel {
        return JBLabel("SVG Icon Downloader").apply {
            font = font.deriveFont(Font.BOLD, 16f)
            border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
        }
    }

    private fun createCenterPanel(): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(createInputPanel(), BorderLayout.NORTH)
            add(createScrollPaneWithResults(), BorderLayout.CENTER)
        }
    }

    private fun createInputPanel(): JPanel {
        return JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(createDirectorySelectionPanel())
            add(Box.createVerticalStrut(10))
            add(createSearchPanel())
            add(Box.createVerticalStrut(15))
        }
    }

    private fun createDirectorySelectionPanel(): JPanel {
        val directoryField = TextFieldWithBrowseButton().apply {
            text = projectService.getProjectRootPath().toString()
            addBrowseFolderListener(
                TextBrowseFolderListener(
                    FileChooserDescriptorFactory.createSingleFolderDescriptor(), project
                )
            )
        }

        return JBPanel<JBPanel<*>>(BorderLayout(0, 5)).apply {
            add(JBLabel("Pick a download directory:"), BorderLayout.NORTH)
            add(directoryField, BorderLayout.CENTER)
        }
    }

    private fun createSearchPanel(): JPanel {
        val searchField = JBTextField()
        val searchButton = JButton("Search")

        searchButton.addActionListener {
            performSearch(searchField.text.trim(), searchButton)
        }

        return JBPanel<JBPanel<*>>(BorderLayout(5, 0)).apply {
            add(JBLabel("Searching for SVG with name:"), BorderLayout.WEST)
            add(searchField, BorderLayout.CENTER)
            add(searchButton, BorderLayout.EAST)
        }
    }

    private fun performSearch(query: String, triggerButton: JButton) {
        svgListModel.clear()

        if (query.isNotEmpty()) {
            triggerButton.isEnabled = false

            val html = fetchSvgRepoPage(query, 1)
            val items = extractSvgItems(html)
            items.forEach { svgListModel.addElement(it) }

            triggerButton.isEnabled = true
        }
    }

    private fun createScrollPaneWithResults(): JScrollPane {
        val svgList = JBList(svgListModel).apply {
            visibleRowCount = 5
            fixedCellHeight = -1
            cellRenderer = SvgItemListRenderer()

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 1) {
                        val index = locationToIndex(e.point)
                        if (index != -1) {
                            val item = svgListModel.getElementAt(index)
                            // Placeholder for item click handling
                        }
                    }
                }
            })
        }

        return JBScrollPane(svgList).apply {
            border = BorderFactory.createLineBorder(JBColor.border())
        }
    }
}

private class SvgItemListRenderer : JBLabel(), ListCellRenderer<SvgItem> {
    override fun getListCellRendererComponent(
        list: JList<out SvgItem>,
        value: SvgItem?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        value?.let {
            text = it.name
            icon = createIcon(it)
            preferredSize = Dimension(280, 100)
        }

        border = BorderFactory.createEmptyBorder(10, 15, 10, 15)
        background = if (isSelected) JBColor.background().darker() else JBColor.background()
        foreground = if (isSelected) JBColor.foreground().brighter() else JBColor.foreground()
        isOpaque = true
        iconTextGap = 20

        return this
    }

    private fun createIcon(svgItem: SvgItem): ImageIcon {
        return try {
            val pngBytes = svgToPngByteArray(svgItem.svgContent, 80f, 80f)
            ImageIcon(pngBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            ImageIcon()
        }
    }
}
