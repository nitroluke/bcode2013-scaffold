package organizedBot;

//import battlecode.common.Direction;
//import battlecode.common.GameConstants;
import battlecode.common.Clock;
import battlecode.common.MapLocation;
import battlecode.common.Robot;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
//import battlecode.common.RobotType;
import battlecode.common.RobotType;

/** The example funcs player is a player meant to demonstrate basic usage of the most common commands.
 * Robots will move around randomly, occasionally mining and writing useless messages.
 * The HQ will spawn soldiers continuously. 
 */
public class RobotPlayer {
	/**
	 * @param rc
	 */
	static SmallStrat ss = new SmallStrat();
	//	public static int counter = 0;
	//	int numEncampments = 0; 
	//	static MapLocation[] encampmentLocationArray = null; 

	public static void run(RobotController rc) {
		while (true) {
			try {
				if(rc.getType() == RobotType.HQ){
					//if(rc.getMapHeight() < 50 && rc.getMapWidth() < 50){
					if(rc.senseEnemyHQLocation().distanceSquaredTo(rc.senseEnemyHQLocation()) < 25){
						// don't know if this is right...	

					//if(1<2){   // strictly for testing
						// run small Map strategy 
						SmallStrat.run(rc);
						
					}
					else{
						// run LargeStrat
						LargeStrat.run(rc);
					}
				}
				
				if (rc.getType() == RobotType.SOLDIER) {
					
					// add for conditioning for map size
					Robot[] enemyRobots = rc.senseNearbyGameObjects(Robot.class,50000,rc.getTeam().opponent());  //list of enemy robots
					if(enemyRobots.length == 0){
						Clock.getRoundNum();  // gets the round number 
						if(Clock.getRoundNum() < 250){
							SmallStrat.goToLocation(SmallStrat.barracks);  // if you are before round 250 travel to the "barracks" or meeting place.
						}else{
							SmallStrat.goToLocation(rc.senseEnemyHQLocation());
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
						SmallStrat.goToLocation(closestEnemy);
					}
				}else{
					SmallStrat.HqCommand();
					//						}
				}


			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}