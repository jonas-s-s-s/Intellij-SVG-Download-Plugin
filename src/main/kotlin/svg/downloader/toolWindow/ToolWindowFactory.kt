package svg.downloader.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import svg.downloader.services.ProjectService
import svg.downloader.utils.SvgItem
import svg.downloader.utils.extractSvgItems
import svg.downloader.utils.fetchSvgRepoPage
import svg.downloader.utils.svgToPngByteArray
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JPanel
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent



class ToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowUI = ToolWindowUI(toolWindow)
        val content = ContentFactory.getInstance().createContent(toolWindowUI.createContentPanel(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

private class ToolWindowUI(private val toolWindow: ToolWindow) {
    private val project: Project = toolWindow.project
    private val projectService: ProjectService = project.service()
    private val listModel = DefaultListModel<SvgItem>()

    fun createContentPanel(): JPanel {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        val titleLabel = JBLabel("SVG Icon Downloader").apply {
            font = font.deriveFont(Font.BOLD, 16f)
            border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
        }
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        val directoryTextField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                TextBrowseFolderListener(
                    FileChooserDescriptorFactory.createSingleFolderDescriptor(), project
                )
            )
            text = projectService.getProjectRootPath().toString()
        }

        val directoryPanel = JBPanel<JBPanel<*>>(BorderLayout(0, 5)).apply {
            add(JBLabel("Pick a download directory:"), BorderLayout.NORTH)
            add(directoryTextField, BorderLayout.CENTER)
        }

        val searchTextField = JBTextField()
        val searchButton = JButton("Search").apply {
            addActionListener {
                listModel.clear()

                val searchTerm = searchTextField.text.trim()
                if (searchTerm.isNotEmpty()) {
                    // Show loading indicator
                    setEnabled(false)

                    val html = fetchSvgRepoPage(searchTerm, 1)
                    val items = extractSvgItems(html)


                    // Update UI with results
                    items.forEach { item ->
                        listModel.addElement(item)
                    }
                    setEnabled(true)
                }
            }
        }

        val searchPanel = JBPanel<JBPanel<*>>(BorderLayout(5, 0)).apply {
            add(JBLabel("Searching for SVG with name:"), BorderLayout.WEST)
            add(searchTextField, BorderLayout.CENTER)
            add(searchButton, BorderLayout.EAST)
        }

        val resultsList = JBList(listModel).apply {
            visibleRowCount = 5
            cellRenderer = CustomListCellRenderer()
            fixedCellHeight = -1

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 1) {
                        val index = locationToIndex(e.point)
                        if (index != -1) {
                            val item = listModel.getElementAt(index)
                        }
                    }
                }
            })
        }

        val scrollPane = JBScrollPane(resultsList).apply {
            border = BorderFactory.createLineBorder(JBColor.border())
        }

        val inputPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(directoryPanel)
            add(Box.createVerticalStrut(10))
            add(searchPanel)
            add(Box.createVerticalStrut(15))
        }

        val centerPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(inputPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        mainPanel.add(centerPanel, BorderLayout.CENTER)
        return mainPanel
    }
}

private class CustomListCellRenderer : JBLabel(), javax.swing.ListCellRenderer<SvgItem> {
    override fun getListCellRendererComponent(
        list: javax.swing.JList<out SvgItem>, value: SvgItem?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): CustomListCellRenderer {

        value?.let {
            val width = 80f
            val height = 80f

            var pngBytes = ByteArray(0)
            try {
                pngBytes = svgToPngByteArray(it.svgContent, width, height)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            text = it.name
            icon = ImageIcon(pngBytes)
            preferredSize = Dimension(
                (width + 200).toInt(), (height + 20).toInt()
            )
        }

        border = BorderFactory.createEmptyBorder(10, 15, 10, 15)
        background = if (isSelected) JBColor.background().darker() else JBColor.background()
        foreground = if (isSelected) JBColor.foreground().brighter() else JBColor.foreground()
        isOpaque = true
        iconTextGap = 20

        return this
    }
}