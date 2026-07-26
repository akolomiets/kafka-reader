package com.npk.kfv

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatSVGUtils
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.UIManager

object ApplicationImages {

    private val logoUrl = if (FlatLaf.isLafDark()) requireResource("/ico/logo_dark.svg") else requireResource("/ico/logo.svg")

    val applicationIcons: List<Image> by lazy { FlatSVGUtils.createWindowIconImages(logoUrl) }
    val logo: Image by lazy { FlatSVGUtils.svg2image(logoUrl, 64, 64) }

    val bannerLogo: Icon by lazy {
        ImageIcon(FlatSVGUtils.svg2image(requireResource(if (FlatLaf.isLafDark()) "/ico/kafka_dark.svg" else "/ico/kafka.svg"), 64, 64))
    }

    enum class Icons(val icon: Icon) {
        Clear(DoubleSVGIcon("/ico/clearCash.svg", "/ico/clearCash_dark.svg")),
        Config(DoubleSVGIcon("/ico/config.svg", "/ico/config_dark.svg")),
        Consumer(DoubleSVGIcon("/ico/consumer.svg", "/ico/consumer_dark.svg")),
        Consuming(DoubleSVGIcon("/ico/consuming.svg", "/ico/consuming_dark.svg")),
        Copy(DoubleSVGIcon("/ico/copy.svg", "/ico/copy_dark.svg")),
        Delete(DoubleSVGIcon("/ico/delete.svg", "/ico/delete_dark.svg")),
        Exit(DoubleSVGIcon("/ico/exit.svg", "/ico/exit_dark.svg")),
        Export(DoubleSVGIcon("/ico/export.svg", "/ico/export_dark.svg")),
        Failure(DoubleSVGIcon("/ico/failure.svg", "/ico/failure_dark.svg")),
        GitHub(DoubleSVGIcon("/ico/github.svg", "/ico/github_dark.svg")),
        Import(DoubleSVGIcon("/ico/import.svg", "/ico/import_dark.svg")),
        Information(DoubleSVGIcon("/ico/info.svg", "/ico/info_dark.svg")),
        Items(DoubleSVGIcon("/ico/items.svg", "/ico/items_dark.svg")),
        Kafka(DoubleSVGIcon("/ico/kafka.svg", "/ico/kafka_dark.svg")),
        Magic(DoubleSVGIcon("/ico/magicResolveToolbar.svg", "/ico/magicResolveToolbar_dark.svg")),
        Minus(DoubleSVGIcon("/ico/minus.svg", "/ico/minus_dark.svg")),
        Open(DoubleSVGIcon("/ico/open.svg", "/ico/open_dark.svg")),
        Plus(DoubleSVGIcon("/ico/plus.svg", "/ico/plus_dark.svg")),
        Producer(DoubleSVGIcon("/ico/producer.svg", "/ico/producer_dark.svg")),
        Refresh(DoubleSVGIcon("/ico/refresh.svg", "/ico/refresh_dark.svg")),
        Reset(DoubleSVGIcon("/ico/reset.svg", "/ico/reset_dark.svg")),
        Run(DoubleSVGIcon("/ico/run.svg", "/ico/run_dark.svg")),
        Scales(DoubleSVGIcon("/ico/scales.svg", "/ico/scales_dark.svg")),
        ServiceAccount(DoubleSVGIcon("/ico/serviceAccount.svg", "/ico/serviceAccount_dark.svg")),
        Settings(DoubleSVGIcon("/ico/settings.svg", "/ico/settings_dark.svg")),
        Stacktrace(DoubleSVGIcon("/ico/stacktrace.svg", "/ico/stacktrace_dark.svg")),
        Star(DoubleSVGIcon("/ico/star.svg", "/ico/star_dark.svg")),
        Stop(DoubleSVGIcon("/ico/stop.svg", "/ico/stop_dark.svg")),
        Success(DoubleSVGIcon("/ico/success.svg", "/ico/success_dark.svg")),
        Table(DoubleSVGIcon("/ico/table.svg", "/ico/table_dark.svg")),
        Test(DoubleSVGIcon("/ico/test.svg", "/ico/test_dark.svg"))
    }

    private class DoubleSVGIcon(lightName: String, darkName: String) : Icon, FlatLaf.DisabledIconProvider {

        private companion object {
            @JvmStatic
            var isDark = FlatLaf.isLafDark()
            init {
                UIManager.addPropertyChangeListener { event ->
                    if (event.propertyName == "lookAndFeel") {
                        isDark = FlatLaf.isLafDark()
                    }
                }
            }
        }

        private val lightIcon = FlatSVGIcon(requireResource(lightName))
        private val darkIcon = FlatSVGIcon(requireResource(darkName))

        override fun getIconWidth(): Int = icon.iconWidth

        override fun getIconHeight(): Int = icon.iconHeight

        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) = icon.paintIcon(c, g, x, y)

        override fun getDisabledIcon(): Icon = icon.disabledIcon

        private val icon inline get() = if (isDark) darkIcon else lightIcon

    }

}
