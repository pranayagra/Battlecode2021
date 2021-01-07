public Boolean move(MapLocation targetLoc) throws GameActionException {


    System.out.println(Clock.getBytecodeNum());
    MapLocation currentLoc = controller.getLocation();
    // Is it ready
    if (!controller.isReady()) {
        return false;
    }
    // Is target out of the map
    if (controller.canSenseRadiusSquared(targetLoc.distanceSquaredTo(currentLoc)) && controller.onTheMap(targetLoc)) {
        return false;
    }

    /*
    Calculate policy to head toward goal location
    Dijkstra locally
    TODO: A*?
    TODO: Route safety penalty
    */
    Double averagePassibility = 0.0;
    for (Direction dir : RobotPlayer.directions) {
        if (controller.onTheMap(currentLoc.add(dir))) {
            averagePassibility += controller.sensePassability(currentLoc.add(dir)) * 1/8;
        }
     }

    class MapLocationWrapper implements Comparable<MapLocationWrapper>{
        MapLocation loc;
        MapLocation pred;
        Double cost;
        public MapLocationWrapper(MapLocation loc, MapLocation pred, Double cost) {
            this.cost = cost;
            this.loc = loc;
            this.pred = pred;
        }
        public int compareTo(MapLocationWrapper a) {
            double res = this.cost - a.cost;
            if (res < 0) return -1; 
            else if (res > 0) return 1;
            else return 0;
        }
    }

    System.out.println(Clock.getBytecodeNum());
    HashMap<MapLocation,Double> visited = new HashMap<MapLocation,Double>();
    PriorityQueue<MapLocationWrapper> pq = new PriorityQueue<MapLocationWrapper>();
    HashMap<MapLocation,MapLocation> pred = new HashMap<MapLocation,MapLocation>();

    pq.add(new MapLocationWrapper(currentLoc, currentLoc, 0.0));

    // Dijkstra
    while (!pq.isEmpty()) {
        System.out.println("Search");
        System.out.println(Clock.getBytecodeNum());
        MapLocationWrapper processLocWrapper = pq.poll();
        MapLocation processLoc = processLocWrapper.loc;
        System.out.println(Clock.getBytecodeNum());
        if (visited.containsKey(processLoc)) continue;
        visited.put(processLoc,processLocWrapper.cost);
        pred.put(processLoc,processLocWrapper.pred);
        if (processLoc.equals(targetLoc)) break;
        System.out.println(Clock.getBytecodeNum());
        for (Direction dir : RobotPlayer.directions) {
            MapLocation adjacentLoc = processLoc.add(dir);
            if (visited.containsKey(adjacentLoc.add(dir))) continue;
            if (!controller.canSenseLocation(adjacentLoc)) continue;
            pq.add(new MapLocationWrapper(adjacentLoc, processLoc, processLocWrapper.cost + 
            (double) 1.0/controller.sensePassability(processLoc)));
        }
        System.out.println(Clock.getBytecodeNum());
        if (controller.canSenseRadiusSquared(((int)Math.pow(Math.abs(processLoc.x-currentLoc.x)+1.0,2) + 
        (int)Math.pow(Math.abs(processLoc.y-currentLoc.y)+1,2)))) {
            pq.add(new MapLocationWrapper(targetLoc, processLoc, 
            processLocWrapper.cost + 
            1.0/controller.sensePassability(processLoc) +
            travelDistance(processLoc, targetLoc) * (double) 1.0/averagePassibility
            ));
        }
        System.out.println(Clock.getBytecodeNum());
    }
    // Backtrack
    MapLocation tracker = targetLoc;
    
    while (!pred.get(tracker).equals(currentLoc)) {
        tracker = pred.get(tracker);
    }

    // Move
    Direction dir = currentLoc.directionTo(tracker);
    if (!naiveMove(dir)) {
        if (!naiveMove(dir.rotateLeft())) {
            return naiveMove(dir.rotateRight());
        }
    }
    return true;
}