import processing.core.PApplet;
import processing.core.PVector;
import java.util.*;
import shiffman.box2d.*;
import org.jbox2d.collision.shapes.*;
import org.jbox2d.common.*;
import org.jbox2d.dynamics.*;
import org.jbox2d.dynamics.*;

public class Game extends PApplet{
    // global project variables go here
    int tWidth;
    int avgPointHeight;
    int shift;
    int variance = 25;
    // ArrayList<PVector> terrainPoints = new ArrayList<PVector>();
    Vec2 bottomLeft, bottomRight;

    // A reference to our box2d world
    Box2DProcessing box2d;

    // An ArrayList of particles that will fall on the surface
    ArrayList<Particle> particles;

    // An object to store information about the uneven surface
    Surface surface;

    ArrayList<Vec2> surfacePoints;

    public static void main(String[] args) {
        PApplet.main("Game");
    }
    public void settings() {size(800, 600);}

    public ArrayList<Vec2> shiftPoints(ArrayList<Vec2> points) {
        ArrayList<Vec2> tempPoints = new ArrayList<>();
        for(Vec2 p : points) tempPoints.add(p.clone());

        int addedHeights = 0;
        for (Vec2 point : tempPoints) {
            addedHeights += height - point.y;
        }
        avgPointHeight = addedHeights / tempPoints.size();
        shift = avgPointHeight-height/2;
        // shift the points so that the average height is the middle of the screen (height/2)
        for (Vec2 point : tempPoints) {
            point.y = lerp(point.y, point.y + shift, 0.05F);
        }
        // returns the modified list of points.
        return tempPoints;
    }

    public void setup() {

        tWidth = width/24;
        surfacePoints = new ArrayList<>();
        // create the starting set of points.
        for(int x = width + 2*tWidth; x >= -tWidth; x -= tWidth)
        {
            surfacePoints.add(new Vec2(x, x == width + 2*tWidth ? (int)(height/2): surfacePoints.get(surfacePoints.size()-1).y + random(-25, 25)));
        }

        // Initialize box2d physics and create the world
        box2d = new Box2DProcessing(this);
        box2d.createWorld();
        // We are setting a custom gravity
        box2d.setGravity(0, -10);

        bottomLeft = new Vec2(-2*tWidth, height + 20);
        bottomRight = new Vec2(width + 2*tWidth, height + 20);

        // Create the empty list
        particles = new ArrayList<Particle>();
        // Create the surface

        surface = new Surface(shiftPoints(surfacePoints));
        smooth();
    }

    public void draw() {
        if (random(1) < 0.5) {
            float sz = random(2,6);
            particles.add(new Particle((float)width/2,10,sz));
        }

        background(66, 197, 245);

        noFill();

        surface.display();

        for (Particle p: particles) {
            p.display();
        }

        for (int i = 0; i < surface.terrainPoints.size(); i++) {
            // move all points left 1 pixel every frame.
            surface.terrainPoints.get(i).x--;
            // if the left-most point reaches -2*tWidth, remove it and generate another point on the far right.
            if(surface.terrainPoints.get(i).x < -2*tWidth) {
                surface.terrainPoints.remove(surface.terrainPoints.size()-1);
                surface.terrainPoints.add(0, new Vec2(width+2*tWidth,  surface.terrainPoints.get(surface.terrainPoints.size()-1).y + random(-variance, variance)));
            }
            ellipse(surface.terrainPoints.get(i).x, surface.terrainPoints.get(i).y, 5, 5);
        }

        surface = new Surface(shiftPoints(surface.terrainPoints));

        for (int i = particles.size()-1; i >= 0; i--) {
            Particle p = particles.get(i);
            if (p.done()) {
                particles.remove(i);
            }
        }

        // shift the time by 1 step (1 frame)
        box2d.step();
    }
    public class Particle {

        // We need to keep track of a Body and a radius
        Body body;
        float r;

        Particle(float x, float y, float r_) {
            r = r_;
            // This function puts the particle in the Box2d world
            makeBody(x,y,r);
        }

        // This function removes the particle from the box2d world
        void killBody() {
            box2d.destroyBody(body);
        }

        // Is the particle ready for deletion?
        boolean done() {
            // Let's find the screen position of the particle
            Vec2 pos = box2d.getBodyPixelCoord(body);
            // Is it off the bottom of the screen?
            if (pos.y > height+r*2) {
                killBody();
                return true;
            }
            return false;
        }

        //
        void display() {
            // We look at each body and get its screen position
            Vec2 pos = box2d.getBodyPixelCoord(body);
            // Get its angle of rotation
            float a = body.getAngle();
            pushMatrix();
            translate(pos.x,pos.y);
            rotate(-a);
            fill(175);
            stroke(0);
            strokeWeight(1);
            ellipse(0,0,r*2,r*2);
            // Let's add a line so we can see the rotation
            line(0,0,r,0);
            popMatrix();
        }

        // Here's our function that adds the particle to the Box2D world
        void makeBody(float x, float y, float r) {
            // Define a body
            BodyDef bd = new BodyDef();
            // Set its position
            bd.position = box2d.coordPixelsToWorld(x,y);
            bd.type = BodyType.DYNAMIC;
            body = box2d.world.createBody(bd);

            // Make the body's shape a circle
            CircleShape cs = new CircleShape();
            cs.m_radius = box2d.scalarPixelsToWorld(r);

            FixtureDef fd = new FixtureDef();
            fd.shape = cs;
            // Parameters that affect physics
            fd.density = 1F;
            fd.friction = 0.01F;
            fd.restitution = 0.3F;

            // Attach fixture to body
            body.createFixture(fd);

            // Give it a random initial velocity (and angular velocity)
            body.setLinearVelocity(new Vec2(random(-10f,10f),random(5f,10f)));
            body.setAngularVelocity(random(-10,10));
        }
    }
    public class Surface {
        // We'll keep track of all of the surface points
        ArrayList<Vec2> terrainPoints;
        ChainShape chain;

        Surface(ArrayList<Vec2> points) {
            terrainPoints = points;
            // This is what box2d uses to put the surface in its world
            chain = new ChainShape();

            // Build an array of vertices in Box2D coordinates
            // from the ArrayList we made
            Vec2[] vertices = new Vec2[terrainPoints.size()];
            for (int i = 0; i < vertices.length; i++) {
                Vec2 edge = box2d.coordPixelsToWorld(terrainPoints.get(i));
                vertices[i] = edge;
            }

            // Create the chain!
            chain.createChain(vertices,vertices.length);

            // The edge chain is now attached to a body via a fixture
            BodyDef bd = new BodyDef();
            bd.position.set(0.0f,0.0f);
            Body body = box2d.createBody(bd);
            // Shortcut, we could define a fixture if we
            // want to specify frictions, restitution, etc.
            body.createFixture(chain,1);
        }

        // A simple function to just draw the edge chain as a series of vertex points
        void display() {
            strokeWeight(2);
            stroke(0);
            fill(68, 117, 28);
            beginShape();
            vertex(bottomRight.x, bottomRight.y);
            vertex(bottomRight.x, bottomRight.y);
            for (Vec2 v: terrainPoints) {
                vertex(v.x, v.y);
                ellipse(v.x, v.y, 10, 10);
            }
            vertex(bottomLeft.x, bottomLeft.y);
            vertex(bottomLeft.x, bottomLeft.y);
            endShape();
        }
    }
}