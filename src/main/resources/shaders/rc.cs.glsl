#version 430
#extension GL_ARB_compute_variable_group_size : enable

#define EPSILON    1e-3
#define LIGHT      1
#define SPHERE     2
#define BOX        3
#define PRIMITIVES 7

layout (local_size_variable) in;
layout (rgba16f, binding = 0) uniform image2D img_output;

struct Primitive {
    int type;
    vec3 center;
    vec3 size;
    vec3 color;
};

struct Hit {
    float time;
    vec3 position;
    vec3 normal;
    vec3 color;
    int type;
};

struct Ray {
    vec3 position;
    vec3 direction;
    float intensity;
    vec3 color;
};

struct Light {
    vec3 position;
    vec3 color;
};

struct Camera {
    vec3 position;
    vec3 direction;
};

uniform Light u_light;
uniform mat4 u_view;

Primitive[] primitives = Primitive[PRIMITIVES](
Primitive(LIGHT, vec3(0.0, 0.0, 0.0), vec3(0.1), vec3(0.0, 0.0, 0.0)),
Primitive(SPHERE, vec3(-4.0, 4.0, 2.0), vec3(2.0), vec3(0.5, 0.01, 0.01)),
Primitive(SPHERE, vec3(0.0, 6.0, 0.0), vec3(1.0), vec3(0.01, 0.5, 0.01)),
Primitive(SPHERE, vec3(4.0, 4.0, -2.0), vec3(2.0), vec3(0.01, 0.01, 0.5)),
Primitive(BOX, vec3(0.0, -2.0, 0.0), vec3(100.0, 1.0, 100.0), vec3(1.0)),
Primitive(BOX, vec3(-2.0, 2.0, -12.0), vec3(4.0, 2.0, 4.0), vec3(0.01, 0.01, 0.01)),
Primitive(BOX, vec3(2.0, 2.0, 12.0), vec3(4.0, 2.0, 4.0), vec3(0.01, 0.01, 0.01))
);

Hit hit_box(Primitive box, Ray ray) {
    Hit hit = { -1.0, { 0.0, 0.0, 0.0 }, { 0.0, 0.0, 0.0 }, { 0.0, 0.0, 0.0 }, 0 };
    vec3 invDir = 1.0f / ray.direction;
    vec3 signum = sign(invDir);

    float tmin = (box.center.x - signum.x * box.size.x - ray.position.x) * invDir.x;
    float tmax = (box.center.x + signum.x * box.size.x - ray.position.x) * invDir.x;
    float tymin = (box.center.y - signum.y * box.size.y - ray.position.y) * invDir.y;
    float tymax = (box.center.y + signum.y * box.size.y - ray.position.y) * invDir.y;

    if (tmin > tymax || tymin > tmax) {
        return hit;
    }
    hit.normal = vec3(-signum.x, 0.0, 0.0);
    if (tymin > tmin) {
        tmin = tymin;
        hit.normal = vec3(0.0, -signum.y, 0.0);
    }
    if (tymax < tmax) {
        tmax = tymax;
    }

    float tzmin = (box.center.z - signum.z * box.size.z - ray.position.z) * invDir.z;
    float tzmax = (box.center.z + signum.z * box.size.z - ray.position.z) * invDir.z;

    if (tmin > tzmax || tzmin > tmax){
        return hit;
    }
    if (tzmin > tmin) {
        tmin = tzmin;
        hit.normal = vec3(0, 0, -signum.z);
    }
    if (tzmax < tmax) {
        tmax = tzmax;
    }

    float t = tmin;
    if (t < 0) {
        t = tmax;
        if (t < 0) {
            return hit;
        }
    }

    hit.time = t;
    hit.position = ray.position + t * ray.direction;
    hit.color = box.color;
    hit.type = box.type;
    return hit;
}

Hit hit_sphere(Primitive sphere, Ray ray) {
    Hit hit = { -1.0, { 0.0, 0.0, 0.0 }, { 0.0, 0.0, 0.0 }, { 0.0, 0.0, 0.0 }, 0 };
    float radius2 = sphere.size.x * sphere.size.x;
    vec3 distance = sphere.center - ray.position;
    float tca = dot(distance, ray.direction);
    float d2 = dot(distance, distance) - tca * tca;
    if (d2 > radius2) {
        return hit;
    }
    float thc = sqrt(radius2 - d2);
    float t0 = tca - thc;
    float t1 = tca + thc;
    if (t0 > t1) {
        float tempT0 = t0;
        t0 = t1;
        t1 = tempT0;
    }
    if (t0 < 0) {
        t0 = t1;
        if (t0 < 0) {
            return hit;
        }
    }
    hit.time = t0;
    hit.position = ray.position + t0 * ray.direction;
    hit.normal = normalize(hit.position - sphere.center);
    hit.color = sphere.color;
    hit.type = sphere.type;
    return hit;
}

Hit hit_primitive(Primitive primitive, Ray ray) {
    switch (primitive.type) {
        case LIGHT:
        case SPHERE:
        return hit_sphere(primitive, ray);
        case BOX:
        return hit_box(primitive, ray);
    }
    return Hit(-1.0, vec3(0.0), vec3(0.0), vec3(0.0), 0);
}

Hit hit_nearest(Ray ray) {
    Hit hit = Hit(-1.0, vec3(0.0), vec3(0.0), vec3(0.0), 0);
    for (int i = 0; i < PRIMITIVES; i++) {
        Primitive primitive = primitives[i];
        Hit hit2 = hit_primitive(primitive, ray);
        if (hit2.time >= 0.0) {
            if (hit.time < 0.0 || hit2.time < hit.time) {
                hit = hit2;
            }
        }
    }
    return hit;
}

vec3 cast_shadow_ray(Ray ray, Hit hit) {
    Ray shadow_ray;
    shadow_ray.position = hit.position;
    shadow_ray.position += -EPSILON * ray.direction;
    shadow_ray.intensity = ray.intensity;
    shadow_ray.color = hit.color * shadow_ray.intensity;
    shadow_ray.direction = normalize(u_light.position - hit.position);

    vec3 ambient = 0.01 * shadow_ray.color;
    Hit shadow_hit = hit_nearest(shadow_ray);
    if (shadow_hit.time >= 0.0 && shadow_hit.type == LIGHT) {

        float lambertian = max(dot(shadow_ray.direction, hit.normal), 0.0);
        if (lambertian < 0.0) {
            return ambient;
        }

        vec3 diffuse = lambertian * shadow_ray.color * u_light.color;
        vec3 specular = vec3(0.0);
        vec3 H = normalize((ray.position - hit.position) + (u_light.position - hit.position));
        float specular_angle = max(dot(H, hit.normal), 0.0);
        if (specular_angle > 0.0) {
            specular = shadow_ray.color * u_light.color * pow(specular_angle, 256.0);
        }

        float attenuation = 1.0 / (1.0 + 0.045 * shadow_hit.time + 0.0075 * shadow_hit.time * shadow_hit.time);
        return ambient + (diffuse + specular) * attenuation;
    }

    return ambient;
}

vec3 cast_primary_ray(Ray ray) {
    vec3 color = vec3(0.0);
    int reflections = 4;
    Ray shadow_ray;
    Ray reflection_ray;
    while (true) {
        Hit hit = hit_nearest(ray);
        if (hit.time < 0.0) {
            return color;
        }

        color += cast_shadow_ray(ray, hit);

        if (reflections > 0) {
            reflection_ray.position = hit.position;
            reflection_ray.position += -EPSILON * ray.direction;
            reflection_ray.direction = reflect(ray.direction, hit.normal);
            reflection_ray.intensity = ray.intensity * 0.9;
            ray = reflection_ray;
            reflections--;
        } else {
            break;
        }
    }

    return color;
}

void main() {
    primitives[0].center = u_light.position;
    primitives[0].color = u_light.color;
    ivec2 pixel_coords = ivec2(gl_GlobalInvocationID.xy);
    ivec2 dims = imageSize(img_output);
    float ratio = float(dims.x) / dims.y;
    float x = (float(pixel_coords.x * 2 - dims.x) / dims.x);
    float y = (float(pixel_coords.y * 2 - dims.y) / dims.y);

    Ray ray;
    ray.position = vec3(0.0, 0.0, 0.0);
    ray.direction = normalize(vec3(y * ratio, x, 2.0));
    ray.intensity = 1.0;
    ray.color = vec3(0.0);

    ray.position = (u_view * vec4(ray.position, 1.0f)).xyz;
    ray.direction = (u_view * vec4(ray.direction, 0.0f)).xyz;

    vec3 color = cast_primary_ray(ray);

    vec4 pixel = vec4(color, 1.0);
    imageStore(img_output, pixel_coords, pixel);
}