package com.tonmatsu.entities.primitive;

import com.tonmatsu.entities.*;
import org.joml.*;

public class Box implements Primitive {
    public final Vector3f center = new Vector3f();
    public final Vector3f size = new Vector3f();
    public final Vector3f color = new Vector3f();
    private final Vector3f invDir = new Vector3f();
    private final Vector3f sign = new Vector3f();

    @Override
    public Vector3f getColor() {
        return color;
    }

    @Override
    public boolean hit(Ray ray, Hit hit) {
        invDir.set(1.0f).div(ray.direction);
        sign.x = invDir.x < 0 ? -1 : 1;
        sign.y = invDir.y < 0 ? -1 : 1;
        sign.z = invDir.z < 0 ? -1 : 1;

        var tmin = (center.x - sign.x * size.x - ray.origin.x) * invDir.x;
        var tmax = (center.x + sign.x * size.x - ray.origin.x) * invDir.x;
        var tymin = (center.y - sign.y * size.y - ray.origin.y) * invDir.y;
        var tymax = (center.y + sign.y * size.y - ray.origin.y) * invDir.y;

        if (tmin > tymax || tymin > tmax)
            return false;
        hit.normal.set(-sign.x, 0, 0);
        if (tymin > tmin) {
            tmin = tymin;
            hit.normal.set(0, -sign.y, 0);
        }
        if (tymax < tmax)
            tmax = tymax;

        var tzmin = (center.z - sign.z * size.z - ray.origin.z) * invDir.z;
        var tzmax = (center.z + sign.z * size.z - ray.origin.z) * invDir.z;

        if (tmin > tzmax || tzmin > tmax)
            return false;
        if (tzmin > tmin) {
            tmin = tzmin;
            hit.normal.set(0, 0, -sign.z);
        }
        if (tzmax < tmax)
            tmax = tzmax;

        var t = tmin;
        if (t < 0) {
            t = tmax;
            if (t < 0)
                return false;
        }

        hit.time = t;
        hit.color.set(color);
        hit.position.set(ray.origin).fma(t, ray.direction);
        return true;
    }
}
