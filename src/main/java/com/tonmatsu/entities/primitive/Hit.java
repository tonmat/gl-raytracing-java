package com.tonmatsu.entities.primitive;

import org.joml.*;

public class Hit {
    public float time;
    public Vector3f position = new Vector3f();
    public Vector3f normal = new Vector3f();
    public Primitive primitive;

    public Hit() {
    }

    public Hit(Hit hit) {
        set(hit);
    }

    public void set(Hit hit) {
        time = hit.time;
        position.set(hit.position);
        normal.set(hit.normal);
        primitive = hit.primitive;
    }
}
