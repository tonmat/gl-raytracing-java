package com.tonmatsu.entities.primitive;

import com.tonmatsu.entities.*;
import org.joml.*;

public interface Primitive {
    Vector3f getColor();

    Hit hit(Ray ray);
}
