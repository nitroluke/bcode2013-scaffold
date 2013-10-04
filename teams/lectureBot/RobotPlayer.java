package lectureBot;

import battlecode.common.*;


/** The example funcs player is a player meant to demonstrate basic usage of the most common commands.
 * Robots will move around randomly, occasionally mining and writing useless messages.
 * The HQ will spawn soldiers continuously. 
 */
public class RobotPlayer {
	/**
	 * @param rc
	 */
	
	int numEncampments = 0; 
	static MapLocation[] encampmentLocationArray = null; 
	static RobotController rc;
	static MapLocation barracks;
    int broadcastChannel = 1971;
	public static void run(RobotController myRC) {
		rc = myRC;
		barracks = findBarracks();
		while (true) {
			try {
				if (rc.getType() == RobotType.SOLDIER) {
					Robot[] enemyRobots = rc.senseNearbyGameObjects(Robot.class,50000,rc.getTeam().opponent());  //list of enemy robots
					if(enemyRobots.length == 0){
					Clock.getRoundNum();  // gets the round number 
					if(Clock.getRoundNum() < 250){
					goToLocation(barracks);  // if you are before round 250 travel to the "barracks" or meeting place.
					}else{
						goToLocation(rc.senseEnemyHQLocation());
					}
					}else{ // else attack the closest enemy
						int closestDist = 50000;
						MapLocation closestEnemy = null;
						for(int i = 0; i < enemyRobots.length;i++){
							Robot enemyBot = enemyRobots[i];
							RobotInfo arobotInfo = rc.senseRobotInfo(enemyBot);
							int dist = arobotInfo.location.distanceSquaredTo(rc.getLocation());
							if(dist < closestDist){
								closestDist = dist;
								closestEnemy = arobotInfo.location;
							}
						}
						goToLocation(closestEnemy);
					}
				}else{
					HqCommand();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			rc.yield(); //end turn
		}
	}

	private static void goToLocation(MapLocation whereToGo) throws GameActionException {

//		if (rc.isActive()) {
//			if(encampmentLocationArray == null){ // find encampments
//				encampmentLocationArray = rc.senseAllEncampmentSquares();
//			}
//			if (counter < 10 && rc.senseMine(rc.getLocation()) == null) { // lay mines behind robots
//				if(rc.senseMine(rc.getLocation())==null)
//					rc.layMine();

			// Send all robots to the passed in argument.
			int dist = rc.getLocation().distanceSquaredTo(whereToGo);
			if(dist > 0){	//dist > 0 && rc.isActive()
				Direction dir = rc.getLocation().directionTo(whereToGo);
				Direction curDir = dir;

				    int[] directionOffSets = {0,1,-1,2,-2};

				lookForDir: for(int d:directionOffSets){
					curDir = Direction.values()[(dir.ordinal()+d+8)%8];
					if(rc.canMove(curDir)){
						break lookForDir;
					}
				}
				Team mine = rc.senseMine(rc.getLocation().add(curDir));
				if(mine != null && mine != rc.getTeam()){
					rc.defuseMine(rc.getLocation().add(curDir));
				}
				else{
					rc.move(curDir);
					rc.setIndicatorString(0, "Last direction moved: "+dir.toString());
				}
			}
		}

//			if (Math.random()<0.01 && rc.getTeamPower()>5) {
//				// Write the number 5 to a position on the message board corresponding to the robot's ID
//				rc.broadcast(rc.getRobot().getID()%GameConstants.BROADCAST_MAX_CHANNELS, 5);
//			}
//		}

//	}

	private static MapLocation findBarracks() {
		MapLocation ourLoc = rc.senseHQLocation();
		MapLocation enemyLoc = rc.senseEnemyHQLocation();
		int x = (enemyLoc.x + 3*ourLoc.x)/4;		// makes the meeting place 1/4 of the way to the enemy.
		int y = (enemyLoc.y + 3*ourLoc.y)/4;
		MapLocation barracks = new MapLocation(x,y);
		return barracks;
	}

	public static void HqCommand() throws GameActionException{
		if (rc.getType() == RobotType.HQ) {
//			if (rc.isActive()) {
				// Spawn a soldier

			    int[] directionOffSets = {0,1,-1,2,-2};

//			    Team mineSpawn = rc.senseMine(rc.getLocation().add(spawnDir)); 
				Direction dir = rc.getLocation().directionTo(rc.senseEnemyHQLocation());  // this does not work!
				Direction spawnDir = dir;
				// spawn robots in the direction of the enemy
				if(rc.senseMine(rc.getLocation().add(dir)) != null){
					lookForDir: for(int d:directionOffSets){
						spawnDir = Direction.values()[(dir.ordinal()+d+8)%8];
						if(rc.canMove(spawnDir)){
							rc.spawn(spawnDir);
							break lookForDir;
						}
					}
				}
				
				if (rc.canMove(dir))
					rc.spawn(dir);
//			}
		}
	}
}
