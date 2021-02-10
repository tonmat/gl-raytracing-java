package com.tonmatsu;

import org.joml.*;
import org.lwjgl.opengl.*;

import static java.util.Objects.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Application {
    private long window;
    private final int width = 768;
    private final int height = 480;
    private final String title = "RT";
    private boolean running;
    private final Vector2i viewport = new Vector2i();
    private boolean viewportResized;
    private final Vector2f cursor = new Vector2f();
    private boolean cursorMoved;

    public static void main(String[] args) {
        new Application().run();
    }

    public void run() {
        init();
        loop();
        dispose();
    }

    private void init() {
        if (!glfwInit())
            throw new RuntimeException("could not initialize glfw!");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        window = glfwCreateWindow(width, height, title, NULL, NULL);
        final var monitor = glfwGetPrimaryMonitor();
        final var videoMode = requireNonNull(glfwGetVideoMode(monitor));
        glfwSetWindowPos(window,
                (videoMode.width() - width) / 2,
                (videoMode.height() - height) / 2);

        glfwSetFramebufferSizeCallback(window, this::handleFramebufferSize);
        glfwSetCursorPosCallback(window, this::handleCursorPosCallback);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        glfwShowWindow(window);
    }

    private void loop() {
        running = true;
        final var thread = new Thread(this::threadLoop);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
        while (!glfwWindowShouldClose(window))
            glfwWaitEvents();
        running = false;
        try {
            thread.join();
        } catch (Exception ignored) {
        }
    }

    private void threadLoop() {
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        final var rt = new RT();
        rt.create(window);
        var lastUpdateTime = 0.0;
        while (running) {
            if (lastUpdateTime == 0.0) {
                lastUpdateTime = glfwGetTime();
                continue;
            }
            final var updateTime = glfwGetTime();
            final var delta = updateTime - lastUpdateTime;
            lastUpdateTime = updateTime;

            if (viewportResized) {
                rt.viewportResized(viewport);
                viewportResized = false;
            }

            if (cursorMoved) {
                rt.cursorMoved(cursor);
                cursorMoved = false;
            }

            rt.update((float) delta);
            glfwSwapBuffers(window);
        }
        rt.dispose();
    }

    private void handleFramebufferSize(long window, int width, int height) {
        viewport.set(width, height);
        viewportResized = true;
        cursorMoved = true;
    }

    private void handleCursorPosCallback(long window, double x, double y) {
        cursor.set(x, y);
        cursorMoved = true;
    }

    private void dispose() {
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
