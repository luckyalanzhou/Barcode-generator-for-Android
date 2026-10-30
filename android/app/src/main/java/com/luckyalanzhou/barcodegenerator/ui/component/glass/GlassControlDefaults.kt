package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** 共用光学预设。新增玻璃控件复用现有材质、着色器和控件角色预设，
 * 不按页面单独增加着色器、色调曲线或无上限的折射强度。
 * 控件自身负责几何形状和手势，材质渲染器不接管这些职责。
 */
internal object GlassControlDefaults {
    const val TabBlurDp = .5f
    const val TabRestRefractionDp = .8f
// 静止时保持克制；移动时只增加有界的光学变化，
// 不把整个选中 Tab 变成移动放大镜。
    const val MaxBackdropRefractionDp = 3.0f
    const val MaxForegroundRefractionDp = 1.6f
}
