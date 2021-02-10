package com.tonmatsu.entities.primitive;

import org.joml.*;

public class Hit {
    public float time;
    public Vector3f color = new Vector3f();
    public Vector3f position = new Vector3f();
    public Vector3f normal = new Vector3f();

    public void set(Hit hit) {
        time = hit.time;
        color.set(hit.color);
        position.set(hit.position);
        normal.set(hit.normal);
    }
}
