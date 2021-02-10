package com.tonmatsu.entities;

import org.joml.*;

public class Ray {
    public final Vector3f origin = new Vector3f();
    public final Vector3f direction = new Vector3f();
    public float intensity = 1.0f;
    public final Vector3f color = new Vector3f();
}
