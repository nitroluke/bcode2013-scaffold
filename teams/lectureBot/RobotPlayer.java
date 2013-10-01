package lectureBot;

import battlecode.common.*;
import battlecode.engine.instrumenter.lang.System;


/** The example funcs player is a player meant to demonstrate basic usage of the most common commands.
 * Robots will move around randomly, occasionally mining and writing useless messages.
 * The HQ will spawn soldiers continuously. 
 */
public class RobotPlayer {
	/**
	 * @param rc
	 */
	
	public static int counter = 0;
	int numEncampments = 0; 
	static MapLocation[] encampmentLocationArray = null; 
//	private static MapLocation [] mineLocations = null;
	private static RobotController rc;
	private static MapLocation barracks;
	private static int radiusSquared = 0;
	
	public static void run(RobotController myRC) {
		rc = myRC;
		barracks = findBarracks();
		while (true) {
			try {
				if (rc.getType() == RobotType.SOLDIER) {
					radiusSquared = (rc.getMapHeight() + rc.getMapWidth())/2;  // 
					Robot[] enemyRobots = rc.senseNearbyGameObjects(Robot.class,50000,rc.getTeam().opponent());  //list of enemy robots
					MapLocation[] minelocations = rc.senseNonAlliedMineLocations(barracks, radiusSquared);
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
							Robot arobot = enemyRobots[i];
							RobotInfo arobotInfo = rc.senseRobotInfo(arobot);
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

		if (rc.isActive()) {
			if(encampmentLocationArray == null){ // find encampments
				encampmentLocationArray = rc.senseAllEncampmentSquares();
			}
//			if(mineLocations == null){
//				mineLocations = rc.senseNonAlliedMineLocations();
//			}
//			if (counter < 10 && rc.senseMine(rc.getLocation()) == null) { // lay mines behind robots
//				if(rc.senseMine(rc.getLocation())==null)
//					rc.layMine();
//				counter++;
//			}else { 
		
			// Send all robots to the passed in argument.
			int dist = rc.getLocation().distanceSquaredTo(whereToGo);
			if(dist > 0){	//dist > 0 && rc.isActive()
				Direction dir = rc.getLocation().directionTo(whereToGo);
				Direction curDir = dir;
				int[] directionOffsets = {0,1,-1,2,-2};
				lookForDir: for(int d:directionOffsets){
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
		}

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
			if (rc.isActive()) {
				// Spawn a soldier

				Direction dir = rc.getLocation().directionTo(rc.senseEnemyHQLocation());
				if (rc.canMove(dir))
					rc.spawn(dir);
			}
		}
	}
}
