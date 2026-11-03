package com.smartwash.feature.lab.ui.fluid

import android.content.Context
import android.opengl.GLES10
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 流体调色板：一组 RGB 色（0..1）。
 *
 * 颜色由页面从清氧设计令牌（[com.smartwash.common.ui.theme.AppColorScheme]）映射后注入，
 * 渲染层不写死任何设计色值；触摸注入与静态种子都从本调色板取色。
 */
class FluidPalette(val colors: Array<out FloatArray>) {

    /** 随机取一色（触摸注入用） */
    fun randomColor(): FloatArray = colors[Random.nextInt(colors.size)]

    /** 顺序取一色（静态种子用，保证整套配色都出现） */
    fun colorAt(index: Int): FloatArray = colors[index.mod(colors.size)]

    companion object {
        fun of(vararg colors: FloatArray): FluidPalette = FluidPalette(colors)
    }
}

/** 渲染器状态（GL 线程写，页面收集） */
data class FluidRenderState(
    val ready: Boolean = false,
    val failed: Boolean = false,
)

/**
 * 指尖流体渲染器：GLSurfaceView.Renderer + stable fluids 求解器 + 全套全屏 shader。
 *
 * 算法（重新实现，非照抄）：每帧执行
 * 1. 涡量（curl）→ 2. 涡量约束（vorticity confinement，手感灵魂）→ 3. 散度
 * → 4. 压力清空（衰减）→ 5. Jacobi 压力迭代 × [PRESSURE_ITERATIONS]
 * → 6. 梯度减法（投影）→ 7. 半拉格朗日平流速度 → 8. 平流染料（乘衰减系数自然消散）。
 *
 * 双分辨率结构：速度/压力场低分辨率（[SIM_RESOLUTION]，保帧率），
 * 染料纹理高分辨率（[DYE_RESOLUTION]，保锐利）。RGBA16F 半精度 FBO，
 * 设备不支持半精度可渲染时回退 RGBA8（[textureInternalFormat]）。
 *
 * 线程：GL 调用全部在 [GLSurfaceView] 的 GL 线程；[enqueueSplat] 从主线程入队，
 * [onDrawFrame] 在 GL 线程出队并应用，避免跨线程 GL 调用。
 * [palette] / [baseColor] / [playing] 为 volatile，主线程写、GL 线程读。
 */
class FluidRenderer : GLSurfaceView.Renderer {

    /** 染料调色板（页面注入令牌色；默认中性色仅作兜底，不参与设计） */
    @Volatile
    var palette: FluidPalette = FluidPalette.of(floatArrayOf(1f, 1f, 1f))

    /** 画布底色（页面注入令牌色；染料以加法叠在底色上） */
    @Volatile
    var baseColor: FloatArray = floatArrayOf(0f, 0f, 0f)

    /** 是否在推进模拟（false = 静帧，仅绘制当前画面） */
    @Volatile
    var playing: Boolean = true

    private val _state = MutableStateFlow(FluidRenderState())
    val state: StateFlow<FluidRenderState> = _state.asStateFlow()

    @Volatile
    private var canvasWidth = 0

    @Volatile
    private var canvasHeight = 0

    @Volatile
    private var aspectRatio = 1f

    private var initialized = false
    private var fieldsReady = false
    private var seeded = false
    private var lastFrameTime = 0L

    // ---------- GL 对象 ----------
    private var vao = 0
    private var vbo = 0
    private var textureInternalFormat = GLES30.GL_RGBA16F
    private var textureFormat = GLES30.GL_RGBA
    private var textureType = GLES30.GL_HALF_FLOAT

    private lateinit var splatProgram: Program
    private lateinit var advectionProgram: Program
    private lateinit var divergenceProgram: Program
    private lateinit var curlProgram: Program
    private lateinit var vorticityProgram: Program
    private lateinit var pressureProgram: Program
    private lateinit var gradientSubtractProgram: Program
    private lateinit var clearProgram: Program
    private lateinit var displayProgram: Program

    private lateinit var dye: DoubleFBO
    private lateinit var velocity: DoubleFBO
    private lateinit var divergence: FBO
    private lateinit var curl: FBO
    private lateinit var pressure: DoubleFBO

    private var fieldWidth = 0
    private var fieldHeight = 0
    private var dyeWidth = 0
    private var dyeHeight = 0

    /** 待应用的手触注入点（主线程入队，GL 线程出队） */
    private val splatQueue = ConcurrentLinkedQueue<SplatPoint>()

    private data class SplatPoint(
        val x: Float,
        val y: Float,
        val dx: Float,
        val dy: Float,
    )

    /** 标记设备不支持 GLES3，页面据此展示降级文案 */
    fun markUnsupported() {
        _state.value = FluidRenderState(ready = false, failed = true)
    }

    /**
     * 入队一次手触注入（像素坐标 + 像素位移；渲染器内部归一化为 uv 与速度单位）。
     * 从主线程调用；内部做宽高比校正，力与参考实现同量级（划得越快涡旋越猛）。
     */
    fun enqueueSplat(xPixels: Float, yPixels: Float, dxPixels: Float, dyPixels: Float) {
        val width = canvasWidth
        val height = canvasHeight
        if (width <= 0 || height <= 0) return
        var dx = dxPixels / width
        var dy = -dyPixels / height
        val aspect = width.toFloat() / height
        if (aspect < 1f) dx *= aspect
        if (aspect > 1f) dy /= aspect
        val u = (xPixels / width).coerceIn(0f, 1f)
        val v = (1f - yPixels / height).coerceIn(0f, 1f)
        if (splatQueue.size < MAX_SPLAT_QUEUE) {
            splatQueue.add(SplatPoint(u, v, dx * SPLAT_FORCE, dy * SPLAT_FORCE))
        }
    }

    /** 重置模拟（GL 线程调用）：清空队列与场，下一帧重新布静态种子 */
    fun reset() {
        if (!initialized || !fieldsReady) return
        splatQueue.clear()
        seeded = false
        clearFields()
    }

    /** 释放全部 GL 资源（GL 线程调用） */
    fun dispose() {
        if (!initialized) return
        releaseFields()
        if (vao != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            vao = 0
        }
        if (vbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
            vbo = 0
        }
        releasePrograms()
        initialized = false
        seeded = false
        _state.value = FluidRenderState(ready = false, failed = false)
    }

    // ---------- GLSurfaceView.Renderer ----------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        if (initialized) releaseGfx()
        try {
            initGfx()
            initialized = true
            seeded = false
            // 场在 onSurfaceChanged 按画布尺寸创建；这里复位尺寸强制重建
            fieldWidth = 0
            fieldHeight = 0
            dyeWidth = 0
            dyeHeight = 0
            _state.value = FluidRenderState(ready = true, failed = false)
        } catch (_: Throwable) {
            initialized = false
            _state.value = FluidRenderState(ready = false, failed = true)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        canvasWidth = width
        canvasHeight = height
        aspectRatio = width.toFloat() / height
        if (!initialized) return
        val sim = getResolution(SIM_RESOLUTION)
        val dyeRes = getResolution(DYE_RESOLUTION)
        if (fieldsReady && sim.first == fieldWidth && sim.second == fieldHeight &&
            dyeRes.first == dyeWidth && dyeRes.second == dyeHeight
        ) {
            return
        }
        initFields(sim.first, sim.second, dyeRes.first, dyeRes.second)
        seeded = false
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!initialized || !fieldsReady || !_state.value.ready) return
        val now = System.nanoTime()
        val dt = if (lastFrameTime == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameTime).toFloat() / 1_000_000_000f).coerceIn(MIN_DT, MAX_DT)
        }
        lastFrameTime = now

        if (!seeded) seedStaticScene()
        drainSplats()
        if (playing) stepSimulation(dt)
        renderDisplay()
    }

    // ---------- 初始化 ----------

    private fun initGfx() {
        val extensions = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_NUM_EXTENSIONS, extensions, 0)
        var halfFloat = false
        for (i in 0 until extensions[0]) {
            val name = GLES30.glGetStringi(GLES10.GL_EXTENSIONS, i) ?: continue
            if (name == "GL_EXT_color_buffer_float" ||
                name == "GL_EXT_color_buffer_half_float"
            ) {
                halfFloat = true
                break
            }
        }
        if (halfFloat) {
            textureInternalFormat = GLES30.GL_RGBA16F
            textureFormat = GLES30.GL_RGBA
            textureType = GLES30.GL_HALF_FLOAT
        } else {
            // 不支持半精度可渲染的设备：回退 8bit（精度下降、可能轻微色带）
            textureInternalFormat = GLES30.GL_RGBA8
            textureFormat = GLES30.GL_RGBA
            textureType = GLES30.GL_UNSIGNED_BYTE
        }

        // 全屏三角形 VAO（VBO 三个顶点覆盖整个视口，片元由插值 uv 驱动）
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vao = vaos[0]
        GLES30.glBindVertexArray(vao)
        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vbo = vbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val vertices = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(vertices).position(0)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * 4,
            buffer,
            GLES30.GL_STATIC_DRAW,
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)

        splatProgram = createProgram(VERTEX_SHADER, SPLAT_SHADER)
        advectionProgram = createProgram(VERTEX_SHADER, ADVECTION_SHADER)
        divergenceProgram = createProgram(VERTEX_SHADER, DIVERGENCE_SHADER)
        curlProgram = createProgram(VERTEX_SHADER, CURL_SHADER)
        vorticityProgram = createProgram(VERTEX_SHADER, VORTICITY_SHADER)
        pressureProgram = createProgram(VERTEX_SHADER, PRESSURE_SHADER)
        gradientSubtractProgram = createProgram(VERTEX_SHADER, GRADIENT_SUBTRACT_SHADER)
        clearProgram = createProgram(VERTEX_SHADER, CLEAR_SHADER)
        displayProgram = createProgram(VERTEX_SHADER, DISPLAY_SHADER)
    }

    private fun initFields(
        fieldWidth: Int,
        fieldHeight: Int,
        dyeWidth: Int,
        dyeHeight: Int,
    ) {
        releaseFields()
        this.fieldWidth = fieldWidth
        this.fieldHeight = fieldHeight
        this.dyeWidth = dyeWidth
        this.dyeHeight = dyeHeight
        dye = DoubleFBO(
            createFbo(dyeWidth, dyeHeight, GLES30.GL_LINEAR),
            createFbo(dyeWidth, dyeHeight, GLES30.GL_LINEAR),
        )
        velocity = DoubleFBO(
            createFbo(fieldWidth, fieldHeight, GLES30.GL_LINEAR),
            createFbo(fieldWidth, fieldHeight, GLES30.GL_LINEAR),
        )
        divergence = createFbo(fieldWidth, fieldHeight, GLES30.GL_NEAREST)
        curl = createFbo(fieldWidth, fieldHeight, GLES30.GL_NEAREST)
        pressure = DoubleFBO(
            createFbo(fieldWidth, fieldHeight, GLES30.GL_NEAREST),
            createFbo(fieldWidth, fieldHeight, GLES30.GL_NEAREST),
        )
        fieldsReady = true
    }

    private fun releaseGfx() {
        releaseFields()
        if (vao != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            vao = 0
        }
        if (vbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
            vbo = 0
        }
        releasePrograms()
    }

    private fun releasePrograms() {
        if (::splatProgram.isInitialized) splatProgram.dispose()
        if (::advectionProgram.isInitialized) advectionProgram.dispose()
        if (::divergenceProgram.isInitialized) divergenceProgram.dispose()
        if (::curlProgram.isInitialized) curlProgram.dispose()
        if (::vorticityProgram.isInitialized) vorticityProgram.dispose()
        if (::pressureProgram.isInitialized) pressureProgram.dispose()
        if (::gradientSubtractProgram.isInitialized) gradientSubtractProgram.dispose()
        if (::clearProgram.isInitialized) clearProgram.dispose()
        if (::displayProgram.isInitialized) displayProgram.dispose()
    }

    private fun releaseFields() {
        fieldsReady = false
        if (::dye.isInitialized) {
            dye.read.dispose()
            dye.write.dispose()
        }
        if (::velocity.isInitialized) {
            velocity.read.dispose()
            velocity.write.dispose()
        }
        if (::divergence.isInitialized) divergence.dispose()
        if (::curl.isInitialized) curl.dispose()
        if (::pressure.isInitialized) {
            pressure.read.dispose()
            pressure.write.dispose()
        }
    }

    /** 按画布宽高比把长边对齐到目标分辨率（与参考实现同构） */
    private fun getResolution(resolution: Int): Pair<Int, Int> {
        var aspect = if (canvasHeight > 0) canvasWidth.toFloat() / canvasHeight else 1f
        if (aspect < 1f) aspect = 1f / aspect
        val min = resolution
        val max = (resolution * aspect).toInt().coerceAtLeast(1)
        return if (canvasWidth >= canvasHeight) max to min else min to max
    }

    // ---------- 模拟步进 ----------

    private fun stepSimulation(dt: Float) {
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(vao)

        // 1. 涡量
        curlProgram.use()
        GLES30.glUniform2f(curlProgram.uniform("texelSize"), velocity.read.texelSizeX, velocity.read.texelSizeY)
        GLES30.glUniform1i(curlProgram.uniform("uVelocity"), velocity.read.attach(0))
        blit(curl)

        // 2. 涡量约束（手感灵魂：把数值耗散掉的旋涡重新推回去）
        vorticityProgram.use()
        GLES30.glUniform2f(vorticityProgram.uniform("texelSize"), velocity.read.texelSizeX, velocity.read.texelSizeY)
        GLES30.glUniform1i(vorticityProgram.uniform("uVelocity"), velocity.read.attach(0))
        GLES30.glUniform1i(vorticityProgram.uniform("uCurl"), curl.attach(1))
        GLES30.glUniform1f(vorticityProgram.uniform("curl"), CURL)
        GLES30.glUniform1f(vorticityProgram.uniform("dt"), dt)
        blit(velocity.write)
        velocity.swap()

        // 3. 散度
        divergenceProgram.use()
        GLES30.glUniform2f(divergenceProgram.uniform("texelSize"), velocity.read.texelSizeX, velocity.read.texelSizeY)
        GLES30.glUniform1i(divergenceProgram.uniform("uVelocity"), velocity.read.attach(0))
        blit(divergence)

        // 4. 压力清空（衰减，防止压力累积）
        clearProgram.use()
        GLES30.glUniform1i(clearProgram.uniform("uTexture"), pressure.read.attach(0))
        GLES30.glUniform1f(clearProgram.uniform("value"), PRESSURE_DECAY)
        blit(pressure.write)
        pressure.swap()

        // 5. Jacobi 压力迭代
        pressureProgram.use()
        GLES30.glUniform2f(pressureProgram.uniform("texelSize"), velocity.read.texelSizeX, velocity.read.texelSizeY)
        GLES30.glUniform1i(pressureProgram.uniform("uDivergence"), divergence.attach(0))
        for (i in 0 until PRESSURE_ITERATIONS) {
            GLES30.glUniform1i(pressureProgram.uniform("uPressure"), pressure.read.attach(1))
            blit(pressure.write)
            pressure.swap()
        }

        // 6. 梯度减法（投影：把速度场修正为无散度）
        gradientSubtractProgram.use()
        GLES30.glUniform2f(gradientSubtractProgram.uniform("texelSize"), velocity.read.texelSizeX, velocity.read.texelSizeY)
        GLES30.glUniform1i(gradientSubtractProgram.uniform("uPressure"), pressure.read.attach(0))
        GLES30.glUniform1i(gradientSubtractProgram.uniform("uVelocity"), velocity.read.attach(1))
        blit(velocity.write)
        velocity.swap()

        // 7. 半拉格朗日平流速度
        advectionProgram.use()
        GLES30.glUniform2f(advectionProgram.uniform("texelSize"), velocity.read.texelSizeX, velocity.read.texelSizeY)
        GLES30.glUniform1i(advectionProgram.uniform("uVelocity"), velocity.read.attach(0))
        GLES30.glUniform1i(advectionProgram.uniform("uSource"), velocity.read.attach(0))
        GLES30.glUniform1f(advectionProgram.uniform("dt"), dt)
        GLES30.glUniform1f(advectionProgram.uniform("dissipation"), VELOCITY_DISSIPATION)
        blit(velocity.write)
        velocity.swap()

        // 8. 平流染料（乘衰减系数自然消散）
        GLES30.glUniform1i(advectionProgram.uniform("uVelocity"), velocity.read.attach(0))
        GLES30.glUniform1i(advectionProgram.uniform("uSource"), dye.read.attach(1))
        GLES30.glUniform1f(advectionProgram.uniform("dissipation"), DENSITY_DISSIPATION)
        blit(dye.write)
        dye.swap()
    }

    private fun renderDisplay() {
        displayProgram.use()
        GLES30.glUniform1i(displayProgram.uniform("uTexture"), dye.read.attach(0))
        GLES30.glUniform3f(
            displayProgram.uniform("uBaseColor"),
            baseColor[0],
            baseColor[1],
            baseColor[2],
        )
        blit(null)
    }

    // ---------- 注入 / 种子 ----------

    private fun drainSplats() {
        var count = 0
        while (count < MAX_SPLAT_QUEUE) {
            val point = splatQueue.poll() ?: break
            applySplat(point.x, point.y, point.dx, point.dy, palette.randomColor(), SPLAT_RADIUS)
            count++
        }
    }

    private fun seedStaticScene() {
        clearFields()
        val rng = Random(System.nanoTime())
        repeat(SEED_SPLATS) { i ->
            val x = 0.15f + rng.nextFloat() * 0.7f
            val y = 0.15f + rng.nextFloat() * 0.7f
            val angle = rng.nextFloat() * (2f * Math.PI.toFloat())
            val speed = 20f + rng.nextFloat() * 40f
            applySplat(
                x,
                y,
                cos(angle) * speed,
                sin(angle) * speed,
                palette.colorAt(i),
                SEED_SPLAT_RADIUS,
            )
        }
        seeded = true
    }

    private fun applySplat(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        color: FloatArray,
        radius: Float,
    ) {
        splatProgram.use()
        GLES30.glUniform1i(splatProgram.uniform("uTarget"), velocity.read.attach(0))
        GLES30.glUniform1f(splatProgram.uniform("aspectRatio"), aspectRatio)
        GLES30.glUniform2f(splatProgram.uniform("point"), x, y)
        GLES30.glUniform3f(splatProgram.uniform("color"), dx, dy, 0f)
        GLES30.glUniform1f(splatProgram.uniform("radius"), correctRadius(radius))
        blit(velocity.write)
        velocity.swap()

        GLES30.glUniform1i(splatProgram.uniform("uTarget"), dye.read.attach(0))
        GLES30.glUniform3f(
            splatProgram.uniform("color"),
            color[0] * DYE_INTENSITY,
            color[1] * DYE_INTENSITY,
            color[2] * DYE_INTENSITY,
        )
        blit(dye.write)
        dye.swap()
    }

    /** 宽画布放大半径，抵消 shader 里 p.x *= aspectRatio 的横向压缩 */
    private fun correctRadius(radius: Float): Float =
        if (aspectRatio > 1f) radius * aspectRatio else radius

    private fun clearFields() {
        clearFbo(velocity.read)
        clearFbo(velocity.write)
        clearFbo(dye.read)
        clearFbo(dye.write)
        clearFbo(pressure.read)
        clearFbo(pressure.write)
        clearFbo(divergence)
        clearFbo(curl)
    }

    private fun clearFbo(fbo: FBO) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo.fbo)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    // ---------- GL 工具 ----------

    private fun blit(target: FBO?) {
        if (target == null) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, canvasWidth, canvasHeight)
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.fbo)
            GLES30.glViewport(0, 0, target.width, target.height)
        }
        // VAO 在 initGfx 末尾解绑，这里每次绘制前重新绑定（静帧路径也要绘制）
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    private fun createFbo(width: Int, height: Int, filter: Int): FBO {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val texture = textures[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            textureInternalFormat,
            width,
            height,
            0,
            textureFormat,
            textureType,
            null,
        )

        val fbos = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        val fbo = fbos[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            texture,
            0,
        )
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Fluid FBO incomplete: ${GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)}")
        }
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return FBO(texture, fbo, width, height, 1f / width, 1f / height)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Fluid shader compile failed: $log")
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Program {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Fluid program link failed: $log")
        }
        return Program(program)
    }

    // ---------- 内部类 ----------

    private class Program(val id: Int) {
        private val uniforms = HashMap<String, Int>()

        fun use() = GLES30.glUseProgram(id)

        fun uniform(name: String): Int =
            uniforms.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

        fun dispose() = GLES30.glDeleteProgram(id)
    }

    private class FBO(
        val texture: Int,
        val fbo: Int,
        val width: Int,
        val height: Int,
        val texelSizeX: Float,
        val texelSizeY: Float,
    ) {
        fun dispose() {
            GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
            GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        }

        fun attach(unit: Int): Int {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            return unit
        }
    }

    private class DoubleFBO(var read: FBO, var write: FBO) {
        fun swap() {
            val temp = read
            read = write
            write = temp
        }
    }

    companion object {
        // ---------- 着色器（GLSL ES 3.00，全屏三角形 + uv） ----------

        private const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPosition;
out vec2 vUv;
void main() {
    vUv = aPosition * 0.5 + 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

        private const val SPLAT_SHADER = """#version 300 es
precision highp float;
precision highp sampler2D;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTarget;
uniform float aspectRatio;
uniform vec3 color;
uniform vec2 point;
uniform float radius;
void main() {
    vec2 p = vUv - point;
    p.x *= aspectRatio;
    vec3 splat = exp(-dot(p, p) / radius) * color;
    vec3 base = texture(uTarget, vUv).xyz;
    fragColor = vec4(base + splat, 1.0);
}
"""

        private const val ADVECTION_SHADER = """#version 300 es
precision highp float;
precision highp sampler2D;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uVelocity;
uniform sampler2D uSource;
uniform vec2 texelSize;
uniform float dt;
uniform float dissipation;
void main() {
    // 半拉格朗日：沿速度场反向回溯一个时间步
    vec2 coord = vUv - dt * texture(uVelocity, vUv).xy * texelSize;
    vec4 result = texture(uSource, coord);
    float decay = 1.0 + dissipation * dt;
    fragColor = result / decay;
}
"""

        private const val DIVERGENCE_SHADER = """#version 300 es
precision highp float;
precision highp sampler2D;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uVelocity;
uniform vec2 texelSize;
void main() {
    float L = texture(uVelocity, vUv - vec2(texelSize.x, 0.0)).x;
    float R = texture(uVelocity, vUv + vec2(texelSize.x, 0.0)).x;
    float T = texture(uVelocity, vUv + vec2(0.0, texelSize.y)).y;
    float B = texture(uVelocity, vUv - vec2(0.0, texelSize.y)).y;
    vec2 C = texture(uVelocity, vUv).xy;
    if (vUv.x - texelSize.x < 0.0) L = -C.x;
    if (vUv.x + texelSize.x > 1.0) R = -C.x;
    if (vUv.y + texelSize.y > 1.0) T = -C.y;
    if (vUv.y - texelSize.y < 0.0) B = -C.y;
    float div = 0.5 * (R - L + T - B);
    fragColor = vec4(div, 0.0, 0.0, 1.0);
}
"""

        private const val CURL_SHADER = """#version 300 es
precision highp float;
precision highp sampler2D;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uVelocity;
uniform vec2 texelSize;
void main() {
    float L = texture(uVelocity, vUv - vec2(texelSize.x, 0.0)).y;
    float R = texture(uVelocity, vUv + vec2(texelSize.x, 0.0)).y;
    float T = texture(uVelocity, vUv + vec2(0.0, texelSize.y)).x;
    float B = texture(uVelocity, vUv - vec2(0.0, texelSize.y)).x;
    float vorticity = R - L - T + B;
    fragColor = vec4(0.5 * vorticity, 0.0, 0.0, 1.0);
}
"""

        private const val VORTICITY_SHADER = """#version 300 es
precision highp float;
precision highp sampler2D;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uVelocity;
uniform sampler2D uCurl;
uniform vec2 texelSize;
uniform float curl;
uniform float dt;
void main() {
    float L = texture(uCurl, vUv - vec2(texelSize.x, 0.0)).x;
    float R = texture(uCurl, vUv + vec2(texelSize.x, 0.0)).x;
    float T = texture(uCurl, vUv + vec2(0.0, texelSize.y)).x;
    float B = texture(uCurl, vUv - vec2(0.0, texelSize.y)).x;
    float C = texture(uCurl, vUv).x;
    vec2 force = 0.5 * vec2(abs(T) - abs(B), abs(R) - abs(L));
    force /= length(force) + 0.0001;
    force *= curl * C;
    force.y *= -1.0;
    vec2 velocity = texture(uVelocity, vUv).xy + force * dt;
    fragColor = vec4(velocity, 0.0, 1.0);
}
"""

        private const val PRESSURE_SHADER = """#version 300 es
precision highp float;
precision highp sampler2D;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uPressure;
uniform sampler2D uDivergence;
uniform vec2 texelSize;
void main() {
    float L = texture(uPressure, vUv - vec2(texelSize.x, 0.0)).x;
    float R = texture(uPressure, vUv + vec2(texelSize.x, 0.0)).x;
    float T = texture(uPressure, vUv + vec2(0.0, texelSize.y)).x;
    float B = texture(uPressure, vUv - vec2(0.0, texelSize.y)).x;
    float divergence = texture(uDivergence, vUv).x;
    float pressure = (L + R + B + T - divergence) * 0.25;
    fragColor = vec4(pressure, 0.0, 0.0, 1.0);
}
"""

        private const val GRADIENT_SUBTRACT_SHADER = """#version 300 es
precision highp float;
precision highp sampler2D;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uPressure;
uniform sampler2D uVelocity;
uniform vec2 texelSize;
void main() {
    float L = texture(uPressure, vUv - vec2(texelSize.x, 0.0)).x;
    float R = texture(uPressure, vUv + vec2(texelSize.x, 0.0)).x;
    float T = texture(uPressure, vUv + vec2(0.0, texelSize.y)).x;
    float B = texture(uPressure, vUv - vec2(0.0, texelSize.y)).x;
    vec2 velocity = texture(uVelocity, vUv).xy - vec2(R - L, T - B);
    fragColor = vec4(velocity, 0.0, 1.0);
}
"""

        private const val CLEAR_SHADER = """#version 300 es
precision highp float;
precision highp sampler2D;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTexture;
uniform float value;
void main() {
    fragColor = value * texture(uTexture, vUv);
}
"""

        private const val DISPLAY_SHADER = """#version 300 es
precision highp float;
precision highp sampler2D;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTexture;
uniform vec3 uBaseColor;
void main() {
    // 染料加法叠在底色上；染料自然消散后回到底色
    vec3 dye = texture(uTexture, vUv).rgb;
    fragColor = vec4(uBaseColor + dye, 1.0);
}
"""

        /** 速度/压力场长边分辨率（低分辨率保帧率） */
        private const val SIM_RESOLUTION = 128

        /** 染料纹理长边分辨率（高分辨率保锐利） */
        private const val DYE_RESOLUTION = 512

        /** Jacobi 压力迭代次数 */
        private const val PRESSURE_ITERATIONS = 20

        /** 压力衰减（每帧乘算，防止压力累积） */
        private const val PRESSURE_DECAY = 0.8f

        /** 涡量约束强度（手感灵魂：越大旋涡越猛） */
        private const val CURL = 30f

        /** 注入半径（占画布比例） */
        private const val SPLAT_RADIUS = 0.25f

        /** 静态种子注入半径（比交互注入略小，构图更聚拢） */
        private const val SEED_SPLAT_RADIUS = 0.12f

        /** 触摸注入力（与参考实现同量级；划得越快速度增量越大） */
        private const val SPLAT_FORCE = 6000f

        /** 速度耗散（平流衰减系数） */
        private const val VELOCITY_DISSIPATION = 0.2f

        /** 染料耗散（平流衰减系数，越大消散越快） */
        private const val DENSITY_DISSIPATION = 1.0f

        /** 染料亮度系数（调色板色乘算后注入） */
        private const val DYE_INTENSITY = 0.25f

        /** 静态种子注入次数 */
        private const val SEED_SPLATS = 8

        /** 单帧最多应用的注入点数（防积压） */
        private const val MAX_SPLAT_QUEUE = 64

        private const val MIN_DT = 0.001f
        private const val MAX_DT = 0.05f
    }
}

/**
 * 指尖流体专用 [GLSurfaceView]：承载 [FluidRenderer]，接管触摸注入与渲染开关。
 * [setPlaying] 在连续渲染与按需渲染间切换——静帧时只绘制一帧，不空转 GPU。
 */
class FluidGLSurfaceView(
    context: Context,
    val renderer: FluidRenderer = FluidRenderer(),
) : GLSurfaceView(context) {

    /** 手触按下回调（页面据此发触觉；移动过程不发，避免高频震动） */
    var onTouchDown: (() -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f

    init {
        if (!supportsGles3(context)) {
            renderer.markUnsupported()
        } else {
            setEGLContextClientVersion(3)
            setRenderer(renderer)
            // 初始按需渲染：静帧（reduced motion）只画一帧，播放时切连续
            renderMode = RENDERMODE_WHEN_DIRTY
            setOnTouchListener { _, event -> handleTouch(event) }
        }
    }

    /** 播放 / 静帧切换（主线程调用） */
    fun setPlaying(playing: Boolean) {
        renderer.playing = playing
        if (playing) {
            renderMode = RENDERMODE_CONTINUOUSLY
        } else {
            renderMode = RENDERMODE_WHEN_DIRTY
            requestRender()
        }
    }

    /** 重置模拟并立即绘一帧（静帧态下也要刷新） */
    fun resetSimulation() {
        queueEvent { renderer.reset() }
        requestRender()
    }

    override fun onDetachedFromWindow() {
        // 在 GL 线程仍运行时释放资源，避免泄漏
        queueEvent { renderer.dispose() }
        super.onDetachedFromWindow()
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                renderer.enqueueSplat(event.x, event.y, 0f, 0f)
                onTouchDown?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                renderer.enqueueSplat(event.x, event.y, dx, dy)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastX = 0f
                lastY = 0f
            }
        }
        return true
    }

    private fun supportsGles3(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager ?: return false
        return activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
    }
}
