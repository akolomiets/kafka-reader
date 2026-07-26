package com.npk.kfv

import com.npk.kfv.components.JBusyPanel
import com.npk.kfv.components.JStacktracePanel
import com.npk.kfv.components.JStatusLabel
import com.npk.swing.jbutton
import com.npk.swing.jpanel
import net.miginfocom.swing.MigLayout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import javax.swing.WindowConstants.DISPOSE_ON_CLOSE

@IntellijGuarded
internal class ComponentsTest {

    @BeforeEach
    fun setUp() {
        UIManager.setLookAndFeel(ApplicationPrefs.lookAndFeelClassName)
        UIHelper.tryUpdateDefaultFont(ApplicationPrefs.fontFamily, ApplicationPrefs.fontSize)
    }

    @Test
    fun `Busy panel`() {
        val edtRef = AtomicReference<Thread>()

        SwingUtilities.invokeLater {
            JFrame().apply {
                defaultCloseOperation = DISPOSE_ON_CLOSE
                preferredSize = Dimension(640, 480)

                glassPane = JBusyPanel()

                contentPane = JPanel().also {
                    it.add(JLabel("Label1"))
                    it.add(JLabel("Label2"))
                    it.add(JButton("Button 1"))
                    it.add(JButton("Button 2"))
                }

                (glassPane as JBusyPanel).let {
                    it.progressText = "<html>Producing a message to the kafka topic: <b>0001.johnson.ferrell</b></html>"
                    it.start()
                }

                pack()
                isVisible = true
            }

            edtRef.set(Thread.currentThread())
        }

        Thread.sleep(Duration.ofSeconds(1))
        requireNotNull(edtRef.get()).join()
    }

    @Test
    fun `Stacktrace panel`() {
        try {
            throw RuntimeException("Runtime exception message")
        } catch (e: Exception) {
            JStacktracePanel.showMessageDialog(null, "Uncaught Exception", e.message, e, false)
        }
    }

    @Test
    fun `Status label`() {
        val edtRef = AtomicReference<Thread>()

        SwingUtilities.invokeLater {
            JFrame().apply {
                defaultCloseOperation = DISPOSE_ON_CLOSE
                preferredSize = Dimension(320, 200)

                contentPane = jpanel(MigLayout("insets 3 10 10 10, gap 10")) {
                    val status1 = JStatusLabel().apply { text = "***" }
                    val status2 = JStatusLabel(false).apply { text = "***" }

                    +(status1 to "pushx, growx, wrap")
                    +(status2 to "pushx, growx, wrap")

                    +jbutton("Run", {
                        when ((1..5).random()) {
                            1 -> {
                                status1.success("Success", Duration.ofSeconds(5))
                                status2.success("Success", Duration.ofSeconds(5))
                            }
                            2 -> {
                                status1.information("Information", Duration.ofSeconds(5))
                                status2.information("Information", Duration.ofSeconds(5))
                            }
                            3 -> {
                                status1.failure("Failure", Duration.ofSeconds(5))
                                status2.failure("Failure", Duration.ofSeconds(5))
                            }
                            4 -> {
                                status1.failure("Failure Ex.", RuntimeException())
                                status2.failure("Failure Ex.", RuntimeException())
                            }
                            5 -> {
                                status1.clean()
                                status1.text = "Plain text 1"
                                status2.clean()
                                status2.text = "Plain text 2"
                            }
                        }
                    })
                }

                pack()
                isVisible = true
            }

            edtRef.set(Thread.currentThread())
        }

        Thread.sleep(Duration.ofSeconds(1))
        requireNotNull(edtRef.get()).join()
    }

}