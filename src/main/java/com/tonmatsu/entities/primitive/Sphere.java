package com.tonmatsu.entities.primitive;

import com.tonmatsu.entities.*;
import org.joml.*;

import static org.joml.Math.*;

public class Sphere implements Primitive {
    public final Vector3f center = new Vector3f();
    public final Vector3f color = new Vector3f();
    public float radius;
    private final Vector3f tmp = new Vector3f();
    private final Hit hit = new Hit();

    @Override
    public Vector3f getColor() {
        return color;
    }

    @Override
    public Hit hit(Ray ray) {
        final var radius2 = radius * radius;
        center.sub(ray.origin, tmp);
        final var tca = tmp.dot(ray.direction);
        final var d2 = tmp.lengthSquared() - tca * tca;
        if (d2 > radius2)
            return null;
        final var thc = sqrt(radius2 - d2);
        var t0 = tca - thc;
        var t1 = tca + thc;
        if (t0 > t1) {
            final var tempT0 = t0;
            t0 = t1;
            t1 = tempT0;
        }
        if (t0 < 0) {
            t0 = t1;
            if (t0 < 0)
                return null;
        }
        hit.time = t0;
        hit.position.set(ray.origin).fma(t0, ray.direction);
        hit.normal.set(hit.position).sub(center).normalize();
        hit.primitive = this;
        return new Hit(hit);
    }
}
