package com.tonmatsu;

import com.tonmatsu.entities.*;
import com.tonmatsu.entities.primitive.*;
import com.tonmatsu.util.*;
import org.joml.Math;
import org.joml.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.system.MemoryUtil.*;

public class RT {
    private long window;
    private Vector2i viewport;
    private float viewportRatio;
    private Vector2f cursor;
    private FloatBuffer pixels;
    private int texture;
    private int quadProgram;
    private Vector3f cameraPosition;
    private Vector2f cameraRotation;
    private Matrix4f view;
    private Vector3f forward;
    private Vector3f up;
    private Vector3f right;
    private Ray ray;
    private ArrayList<Primitive> primitives;
    private Light light;
    private float time;

    public void create(long window) {
        this.window = window;
        viewport = new Vector2i();
        cursor = new Vector2f();
        glClearColor(0.1f, 0.12f, 0.14f, 1.0f);
        glEnable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        pixels = memAllocFloat(1);

        texture = createTexture();

        quadProgram = createQuadProgram();

        cameraPosition = new Vector3f(0, 4, 2);
        cameraRotation = new Vector2f(0, 0);
        view = new Matrix4f();
        forward = new Vector3f();
        up = new Vector3f();
        right = new Vector3f();
        ray = new Ray();
        primitives = new ArrayList<>();
        primitives.add(createSphere(-4, 4, 2, 2, 0.5f, 0.01f, 0.01f));
        primitives.add(createSphere(0, 6, 0, 1, 0.01f, 0.5f, 0.01f));
        primitives.add(createSphere(4, 4, -2, 2, 0.01f, 0.01f, 0.5f));
        primitives.add(createBox(0, -2, 0, 100, 1, 100, 1, 1, 1));
        primitives.add(createBox(-2, 2, -12, 4, 2, 4, 0.01f, 0.01f, 0.01f));
        primitives.add(createBox(2, 2, 12, 4, 2, 4, 0.01f, 0.01f, 0.01f));
        primitives.add(light = createLight(0, 0, 0, 1, 1, 1));

        time = 0.0f;
    }

    public void viewportResized(Vector2i viewport) {
        glViewport(0, 0, viewport.x, viewport.y);
        this.viewport.set(viewport).div(4);
        memFree(pixels);
        pixels = memAllocFloat(viewport.x * viewport.y * 4);
        viewportRatio = (float) viewport.x / viewport.y;
        resizeTexture(texture);
    }

    public void cursorMoved(Vector2fc cursor) {
        this.cursor.set(cursor);
    }

    public void update(float delta) {
        time += delta;

        glClear(GL_COLOR_BUFFER_BIT);

        light.center.x = Math.sin(time * 0.7f) * 16.0f;
        light.center.y = Math.cos(time * 0.03f) * 4.0f + 8.0f;
        light.center.z = Math.cos(time * 1.1f) * 16.0f;

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
        pixels.clear();
        for (int y = 0; y < viewport.y; y++) {
            final var ay = ((float) y / viewport.y) - 0.5f;
            for (int x = 0; x < viewport.x; x++) {
                final var ax = (((float) x / viewport.x) - 0.5f) / viewportRatio;
                ray.origin.set(cameraPosition);
                ray.direction.set(ay, ax, 0).sub(0, 0, 0.5f);
                ray.direction.mulDirection(view);
                ray.direction.normalize();

                final var color = castRay(ray, primitives, light, 4);
                pixels.put(color.x);
                pixels.put(color.y);
                pixels.put(color.z);
                pixels.put(1);
            }
        }

        setTextureSubData(texture, pixels.flip());

        view.identity()
                .rotateX(cameraRotation.x)
                .rotateY(cameraRotation.y);

        glUseProgram(quadProgram);
        glBindTexture(GL_TEXTURE_2D, texture);
        glBegin(GL_QUADS);
        glVertexAttrib2f(0, -1, -1);
        glVertexAttrib2f(1, 0, 1);
        glVertexAttrib2f(0, +1, -1);
        glVertexAttrib2f(1, 1, 1);
        glVertexAttrib2f(0, +1, +1);
        glVertexAttrib2f(1, 1, 0);
        glVertexAttrib2f(0, -1, +1);
        glVertexAttrib2f(1, 0, 0);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, GL_NONE);
        glUseProgram(GL_NONE);
    }

    public void dispose() {

    }

    private Sphere createSphere(float centerX, float centerY, float centerZ, float radius, float r, float g, float b) {
        final var sphere = new Sphere();
        sphere.center.set(centerX, centerY, centerZ);
        sphere.radius = radius;
        sphere.color.set(r, g, b);
        return sphere;
    }

    private Box createBox(float centerX, float centerY, float centerZ, float sizeX, float sizeY, float sizeZ, float r, float g, float b) {
        final var box = new Box();
        box.center.set(centerX, centerY, centerZ);
        box.size.set(sizeX, sizeY, sizeZ);
        box.color.set(r, g, b);
        return box;
    }

    private Light createLight(float centerX, float centerY, float centerZ, float r, float g, float b) {
        final var light = new Light();
        light.center.set(centerX, centerY, centerZ);
        light.radius = 0.1f;
        light.color.set(r, g, b);
        return light;
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
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA16F, viewport.x, viewport.y);
        glBindTexture(GL_TEXTURE_2D, GL_NONE);
        return texture;
    }

    private void resizeTexture(int texture) {
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA16F, viewport.x, viewport.y);
        glBindTexture(GL_TEXTURE_2D, GL_NONE);
    }

    private void setTextureSubData(int texture, FloatBuffer pixels) {
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, viewport.x, viewport.y, GL_RGBA, GL_FLOAT, pixels);
        glBindTexture(GL_TEXTURE_2D, GL_NONE);
    }

    private Hit getNearestHit(Ray ray) {
        Hit hit = null;
        for (final var p : primitives) {
            final var hit2 = p.hit(ray);
            if (hit2 != null) {
                if (hit == null || hit2.time < hit.time)
                    hit = hit2;
            }
        }
        return hit;
    }

    private Vector3f castRay(Ray ray, List<Primitive> primitives, Light light, int reflections) {
        ray.intensity = 1.0f;
        final var color = new Vector3f();
        final var shadowRay = new Ray();
        final var reflectionRay = new Ray();
        final var temp = new Vector3f();
        while (true) {
            final var hit = getNearestHit(ray);
            if (hit == null)
                return color;

            // cast shadow ray
            shadowRay.origin.set(hit.position);
            shadowRay.origin.fma(-1e-3f, ray.direction);
            shadowRay.intensity = ray.intensity;
            shadowRay.color.set(hit.primitive.getColor()).mul(shadowRay.intensity);

            shadowRay.direction.set(light.center).sub(hit.position).normalize();
            final var shadowRayHit = castRay(shadowRay, primitives);
            color.fma(0.01f, shadowRay.color);
            if (shadowRayHit != null && shadowRayHit.primitive == light) {
                final var attenuation = 1.0f / (1.0f + 0.045f * shadowRayHit.time + 0.0075f * shadowRayHit.time * shadowRayHit.time);
                final var lambertian = Math.max(shadowRay.direction.dot(hit.normal), 0);
                if (lambertian > 0) {
                    temp.set(shadowRay.color).mul(light.getColor());
                    color.fma(lambertian * attenuation, temp);

                    temp.set(ray.origin).sub(hit.position).add(light.center).sub(hit.position).normalize();
                    final var specAngle = Math.max(temp.dot(hit.normal), 0);
                    if (specAngle > 0) {
                        temp.set(shadowRay.color).mul(light.getColor());
                        color.fma(attenuation * (float) java.lang.Math.pow(specAngle, 256.0), temp);
                    }
                }
//                temp.set(shadowRay.color).mul(light.getColor());
//                color.fma(Math.min(attenuation, 1.0f), temp);
            }

            if (reflections > 0) {
                // cast reflection ray
                reflectionRay.origin.set(hit.position);
                reflectionRay.origin.fma(-1e-3f, ray.direction);
                reflectionRay.direction.set(ray.direction).reflect(hit.normal);
                reflectionRay.intensity = ray.intensity * 0.9f;
                ray = reflectionRay;
                reflections--;
            } else {
                break;
            }
        }
        return color;
    }

    public Hit castRay(Ray ray, List<Primitive> primitives) {
        for (Primitive primitive : primitives) {
            final var hit = primitive.hit(ray);
            if (hit != null)
                return hit;
        }
        return null;
    }
}
