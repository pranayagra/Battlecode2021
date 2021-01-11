package teambot.battlecode2021;

import battlecode.common.*;
import teambot.*;
import teambot.battlecode2021.util;
import java.util.stream;
import java.util.PriorityQueue;


// EC Channels: | State: 2 | State Strategy: 3 | Instruction: 4 | Param 1: 3 | Param 2: 3 | Param 3 | 6 | 


public class EnlightenmentCenterBot implements RunnableBot {
    private RobotController rc;
    private FlagBuilder fb;
    private Directions[] spawns;
    private Directions[] walls;
    private PriorityQueue<RobotType> buildQueue;
    private ArrayList<Integer> muckrakers;
    private ArrayList<Integer> politicians;
    private HashSet<Integer> units;
    private ArrayList<MapLocation> enemies;
    private HashMap<Integer, Integer> signal_cache;
    private final int SEED = 229414;

    public EnlightenmentCenterBot(RobotController rc) throws GameActionException {
        this.rc = rc;
        init();
    }

    @Override
    public void init() throws GameActionException {
        // Set flag to init state
        // Determine optimal number/location of spawns to leave open
        // // Calculate maximum spawn rate based on passability
        build_cd = (2.0 / rc.sensePassability(rc.getLocation()));
        // Most passable spawn locations
        List<Direction> dir = Lists.newArrayList(directions);
        Comparator<Direction> comp = (d1, d2) -> rc.sensePassability(rc.adjacentLocation(d1)) - rc.sensePassability(rc.adjacentLocation(d2));
        dir.sort(comp.reversed());
        num_spawns = Math.ceil(10 / build_cd);
        while (num_spawns < dir.size()) {
            dir.remove(num_spawns);
        }
        this.fb = new FlagBuilder(SEED);
    }

    @Override
    public void turn() throws GameActionException {
        if (Debug.debug) {
            System.out.println("I have " + rc.getInfluence() + " " + rc.getConviction());
        }

        // Setup agnostic actions
        fb.setParity(1);

        // sense
        for (info : rc.senseNearbyRobots(2, rc.getTeam())) {
            units.add(info.getID());
        }

        // scan flags for danger
        for (id : units) {
            if (rc.canGetFlag(id)) {
                flag = rc.getFlag(id);
                inst = fb.getInstruction(flag);
                if (inst == SIGNALE.getCode()) {
                    enemies.add(fb.getLocation(flag));
                } else if (inst == SIGNALF1.getCode()) {

                } else if (inst == SIGNALF2.getCode()) {

                }
            
            }
        }

        // Turn wrap up

        // Set flag
        rc.setFlag(fb.getFlag());
        spawn appropriate unit
        if (rc.isReady() && buildQueue.size() > 0) {
            type = buildQueue.poll();
            if (type == SLANDERER) {
                cost = 42;
            } else if (type == MUCKRAKER) {
                cost = muckrakers.sort().reversed().remove(0);
            } else {
                cost = politicians.sort().reversed().remove(0);
            }
            for (d: dir) {
                if (rc.canBuildRobot(type, d, cost)) {
                    rc.buildRobot(type, d, cost);
                    break;
                }
            }
        }
    }
}
