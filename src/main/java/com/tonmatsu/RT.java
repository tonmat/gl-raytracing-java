package com.tonmatsu;

import com.tonmatsu.util.*;
import org.joml.Math;
import org.joml.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.ARBComputeVariableGroupSize.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryStack.*;

public class RT {
    private long window;
    private Vector3i workGroupCount;
    private Vector3i workGroupSize;
    private int workGroupInvocations;
    private Vector2i viewport;
    private Vector2f cursor;
    private int vao;
    private int ibo;
    private int vbo;
    private int texture;
    private int quadProgram;
    private int rcProgram;
    private int lightPositionUniformLocation;
    private int lightColorUniformLocation;
    private int viewUniformLocation;
    private Vector3f cameraPosition;
    private Vector2f cameraRotation;
    private Matrix4f view;
    private Vector3f forward;
    private Vector3f up;
    private Vector3f right;
    private Vector3f lightPosition;
    private Vector3f lightColor;
    private float time;

    public void create(long window) {
        workGroupCount = new Vector3i();
        workGroupCount.x = glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0);
        workGroupCount.y = glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1);
        workGroupCount.z = glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 2);
        System.out.printf("max global (total) work group counts x:%d y:%d z:%d\n", workGroupCount.x, workGroupCount.y, workGroupCount.z);

        workGroupSize = new Vector3i();
        workGroupSize.x = glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0);
        workGroupSize.y = glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 1);
        workGroupSize.z = glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 2);
        System.out.printf("max local (in one shader) work group sizes x:%d y:%d z:%d\n", workGroupSize.x, workGroupSize.y, workGroupSize.z);

        workGroupInvocations = glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS);
        System.out.printf("max local work group invocations %d\n", workGroupInvocations);

        this.window = window;
        viewport = new Vector2i();
        cursor = new Vector2f();

        vao = createVertexArray();
        ibo = createIndexBuffer(0, 1, 2, 2, 3, 0);
        vbo = createVertexBuffer(
                -1, -1, 0, 0,
                +1, -1, 1, 0,
                +1, +1, 1, 1,
                -1, +1, 0, 1);
        glBindVertexArray(GL_NONE);
        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);

        texture = createTexture();

        quadProgram = createQuadProgram();

        rcProgram = createComputeProgram();
        lightPositionUniformLocation = glGetUniformLocation(rcProgram, "u_light.position");
        lightColorUniformLocation = glGetUniformLocation(rcProgram, "u_light.color");
        viewUniformLocation = glGetUniformLocation(rcProgram, "u_view");

        cameraPosition = new Vector3f(0, 4, 2);
        cameraRotation = new Vector2f(0, 0);
        view = new Matrix4f();
        forward = new Vector3f();
        up = new Vector3f();
        right = new Vector3f();

        lightPosition = new Vector3f();
        lightColor = new Vector3f(1, 1, 1);

        time = 0.0f;
    }

    public void viewportResized(Vector2i viewport) {
        glViewport(0, 0, viewport.x, viewport.y);
        this.viewport.set(viewport);
        resizeTexture(texture);
    }

    public void cursorMoved(Vector2fc cursor) {
        this.cursor.set(cursor);
    }

    public void update(float delta) {
        time += delta;

        glClear(GL_COLOR_BUFFER_BIT);

        lightPosition.x = Math.sin(time * 0.7f) * 16.0f;
        lightPosition.y = Math.cos(time * 0.03f) * 4.0f + 12.0f;
        lightPosition.z = Math.cos(time * 1.1f) * 16.0f;

        view.positiveZ(forward).mul(-1);
        view.positiveY(up);
        view.positiveX(right);

        var moveRight = 0.0f;
        var moveForward = 0.0f;
        var moveUp = 0.0f;
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) moveRight--;
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) moveRight++;
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) moveForward--;
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) moveForward++;
        if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) moveUp--;
        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) moveUp++;

        cameraPosition.fma(moveForward * delta * 4f, forward);
        cameraPosition.fma(moveRight * delta * 4f, right);
        cameraPosition.fma(moveUp * delta * 4f, up);

        cameraRotation.set(cursor.y * 0.001f, cursor.x * 0.001f);

        view.invert();

        {
            glUseProgram(rcProgram);
            glUniform3f(lightPositionUniformLocation, lightPosition.x, lightPosition.y, lightPosition.z);
            glUniform3f(lightColorUniformLocation, lightColor.x, lightColor.y, lightColor.z);
            try (final var stack = stackPush()) {
                glUniformMatrix4fv(viewUniformLocation, false, view.get(stack.mallocFloat(16)));
            }
            final var g = 8;
            glDispatchComputeGroupSizeARB(
                    viewport.x / g, viewport.y / g, 1,
                    g, g, 1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
            glUseProgram(GL_NONE);
        }

        view.identity()
                .rotateX(cameraRotation.x)
                .rotateY(cameraRotation.y)
                .translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        glUseProgram(quadProgram);
        glBindTexture(GL_TEXTURE_2D, texture);
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glBindVertexArray(GL_NONE);
        glBindTexture(GL_TEXTURE_2D, GL_NONE);
        glUseProgram(GL_NONE);
    }

    public void dispose() {
        glDeleteTextures(texture);
        glDeleteProgram(quadProgram);
        glDeleteProgram(rcProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(ibo);
        glDeleteBuffers(vbo);
    }

    private int createQuadProgram() {
        final var program = glCreateProgram();
        final var vs = createShader(GL_VERTEX_SHADER, "shaders/quad.vs.glsl");
        final var fs = createShader(GL_FRAGMENT_SHADER, "shaders/quad.fs.glsl");
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        glDeleteShader(vs);
        glDeleteShader(fs);
        final var status = glGetProgrami(program, GL_LINK_STATUS);
        if (status == GL_FALSE) {
            System.err.printf(
                    "could not link program!\n%s\n",
                    glGetProgramInfoLog(program));
            glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private int createComputeProgram() {
        final var program = glCreateProgram();
        final var cs = createShader(GL_COMPUTE_SHADER, "shaders/rc.cs.glsl");
        glAttachShader(program, cs);
        glLinkProgram(program);
        glDeleteShader(cs);
        final var status = glGetProgrami(program, GL_LINK_STATUS);
        if (status == GL_FALSE) {
            System.err.printf(
                    "could not link program!\n%s\n",
                    glGetProgramInfoLog(program));
            glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private int createShader(int type, String asset) {
        final var source = AssetUtils.getString(asset);
        final var shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        final var status = glGetShaderi(shader, GL_COMPILE_STATUS);
        if (status == GL_FALSE) {
            System.err.printf(
                    "could not compile shader!\n%s\n%s\n",
                    asset, glGetShaderInfoLog(shader));
            glDeleteShader(shader);
            return GL_NONE;
        }
        return shader;
    }

    private int createTexture() {
        final var texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA32F, viewport.x, viewport.y);
        glBindImageTexture(0, texture, 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);
        glBindTexture(GL_TEXTURE_2D, GL_NONE);
        return texture;
    }

    private int createVertexArray() {
        final var array = glGenVertexArrays();
        glBindVertexArray(array);
        return array;
    }

    private int createIndexBuffer(int... data) {
        final var buffer = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        return buffer;
    }

    private int createVertexBuffer(float... data) {
        final var buffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        return buffer;
    }

    private void resizeTexture(int texture) {
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA32F, viewport.x, viewport.y);
        glBindTexture(GL_TEXTURE_2D, GL_NONE);
    }
}
