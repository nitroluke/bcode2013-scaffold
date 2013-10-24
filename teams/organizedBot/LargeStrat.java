package organizedBot;


import battlecode.common.*;

public class LargeStrat {

	static RobotController rc; // the robot controller
	static int mult = 271; // messaging adjustment encoding
	static int status = 1;// 1 is don't lay mines, 2 is lay mines
	static MapLocation myLoc; // Place holder for map location, 
	static MapLocation hqLoc; // Place holder for HQ location.
	static MapLocation enemyHQLoc; // Place holder for enemy HQ Location.
	static MapLocation[] encampments; // array of encampments.

	/*
	 * HEURISTICS FOR SWARM MIND TODO: optimize these heuristics.
	 */
	// Attraction from neutral or enemy encamps when no enemies are around.
	final static int peaceEncampAttraction = 10;
	// Repulsion from allied units when no enemies are around.
	final static int peaceAlliedRepulsion = -3;
	// the margin we need to out-number enemy by in order to attack.
	final static int outNumMargin = 2;
	// Attraction from allied units when out-numbering enemies are around.
	final static int defAlliedAttraction = 5;
	// Repulsion from enemy units when there's more of them than allied.
	final static int defEnemyRepulsion = -10;
	// Attraction from allied units when we out-number enemy.
	final static int offAlliedAttraction = 5;
	// Attraction from near enemy units when we out-number enemy.
	final static int offNearEnemyAttraction = 10;
	// Attraction from far enemy units when we out-number enemy.
	final static int offFarEnemyAttraction = 10;
	// variable to control vision used for swarm behavior.
	static int vision = 14;

	/**
	 * main function to determine type of robot and run that type's code
	 * 
	 * @param myRC
	 *            robot controller for calling functions.
	 */
	public static void run(RobotController myRC) {
		rc = myRC;
		if (rc.getTeam() == Team.A)
			mult = 104;

		while (true) {
			try {
				if (rc.getType() == RobotType.SOLDIER) {
					soldierCode();
				} else if (rc.getType() == RobotType.HQ) {
					hqCode();
				} else if (rc.getType() == RobotType.ARTILLERY) {
					artilleryCode();
				}

			} catch (Exception e) {
				System.out.println("caught exception before it killed us:");
				e.printStackTrace();
			}
			rc.yield();
		}
	}

	/**
	 * SOLDIER Method for controlling a SOLDIER robot.
	 */
	private static void soldierCode() {
		// the rally point we are heading for.
		MapLocation rallyPt;
		// the number of encampments we own.
		int encampsCaptured = 0;
		// the base channel we are communicating on.
		int channel;
		// Initialize our HQ and enemy HQ locations.
		if (hqLoc == null) {
			hqLoc = rc.senseHQLocation();
		}
		if (enemyHQLoc == null) {
			enemyHQLoc = rc.senseEnemyHQLocation();
		}

		while (true) {
			try {
				if (rc.isActive()) {
					// set our location
					myLoc = rc.getLocation();
					rallyPt = myLoc;
					// get the communication channel.
					channel = getChannel();

					// receive rally point from HQ
					// check if we have enough power first
					if (rc.getTeamPower() > 3) {
						MapLocation received = IntToMaplocation(rc
								.readBroadcast(channel));
						if (received != null) {
							rallyPt = received;
							rc.setIndicatorString(0,
									"goal: " + rallyPt.toString());
						}
						// receive mining command
						// TODO: use the below command
						int ir = rc.readBroadcast(channel + 1);
						if (ir != 0 && ir <= 2) {
							status = ir;
						}
						encampsCaptured = rc.readBroadcast(channel + 2);
					}

					// check distance to hq
					/*
					 * TODO: see if using below code with increased mine
					 * density. if (myLoc.distanceSquaredTo(hqLoc) < 150) {
					 * status = 2; } else { status = 1; }
					 */

					// see what's around us
					Robot[] allies = rc.senseNearbyGameObjects(Robot.class,
							vision, rc.getTeam());
					Robot[] enemies = rc.senseNearbyGameObjects(Robot.class,
							10000000, rc.getTeam().opponent());
					Robot[] nearbyEnemies = rc.senseNearbyGameObjects(
							Robot.class, vision, rc.getTeam().opponent());
					// TODO: we need to balance the range of below somehow, the
					// bigger the harder it is to compute, but more useful
					MapLocation[] nearbyEncamps = rc.senseEncampmentSquares(
							myLoc, vision, Team.NEUTRAL);

					/*
					 * TODO: We need to explore not using mines to attack, or in
					 * emergency. or to capture encampments
					 */
					if (status == 1) {// don't lay mines
						// move toward received goal, using swarm behavior
						freeGo(rallyPt, allies, enemies, nearbyEnemies,
								nearbyEncamps, encampsCaptured);
						// lay mines in a checkerboard pattern if status == 2
						// and there are no nearby enemies
					} else if ((status == 2 && (nearbyEnemies.length == 0))) {
						if (goodPlaceForMine(myLoc)
								&& rc.senseMine(myLoc) == null) {
							rc.layMine();
						} else {
							// if laying mines and bad spot, use swarm behavior
							freeGo(rallyPt, allies, enemies, nearbyEnemies,
									nearbyEncamps, encampsCaptured);
						}
					} else { // DON'T EVER DELETE THIS OR YOU WILL DIE!!!!
						// if not laying mines, (enemy close) use swarm behavior
						freeGo(rallyPt, allies, enemies, nearbyEnemies,
								nearbyEncamps, encampsCaptured);
					}
				}
			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}
			rc.yield();
		}
	}

	/**
	 * Method to determine good location for mines.
	 * 
	 * @param location
	 *            location in question for mine laying
	 * @return boolean indicating if the location is in checkerboard pattern.
	 */
	private static boolean goodPlaceForMine(MapLocation location) {
		// TODO experiment with pickaxe both with and without gaps.
		// return ((3*location.x+location.y)%8==0);//pickaxe with gaps
		// return ((2*location.x+location.y)%5==0);//pickaxe without gaps
		return ((location.x + location.y) % 2 == 0);// checkerboard
	}

	/**
	 * SWARM BEHAVIOR MOVEMENT SYSTEM
	 * 
	 * Uses heuristics to determine the next location the robot should move to
	 * 
	 * @param target
	 *            the goal location for the robot to move towards.
	 * @param allies
	 *            array of nearby allies.
	 * @param enemies
	 *            array of all enemies that can be sensed.
	 * @param nearbyEnemies
	 *            array of enemies near the robot.
	 * @param capturedEncamps
	 *            , int of how many encampments we have captured.
	 * @throws GameActionException
	 */
	private static void freeGo(MapLocation target, Robot[] allies,
			Robot[] enemies, Robot[] nearbyEnemies,
			MapLocation[] nearbyEncamps, int capturedEncamps)
			throws GameActionException {
		/*
		 * This robot will be attracted to the goal and repulsed from other
		 * things
		 */
		Direction toTarget = myLoc.directionTo(target);
		int targetWeighting = targetWeight(myLoc.distanceSquaredTo(target));
		// toward target,
		MapLocation goalLoc = myLoc.add(toTarget, targetWeighting);
		// Boolean for to decide how to avoid mines
		boolean enemiesClose = false;

		/*
		 * TODO weighted by the distance so that if close enough, it doesn't
		 * freak out.
		 */
		// No nearby enemies, spread out.
		if (enemies.length == 0) {
			// find closest allied robot. repel away from that robot.
			if (allies.length > 0) {
				if (nearbyEncamps.length > 0) {
					MapLocation closestEncamp = findClosestMapLocation(nearbyEncamps);
					goalLoc = goalLoc.add(myLoc.directionTo(closestEncamp),
					/*
					 * TODO Balance below, each additional encampment is more
					 * expensive.
					 */
					peaceEncampAttraction - (capturedEncamps / 2));
				}
				MapLocation closestAlly = findClosestRobot(allies);
				goalLoc = goalLoc.add(myLoc.directionTo(closestAlly),
						peaceAlliedRepulsion);
			}
			// We don't out number the nearby enemies, clump together and run.
		} else if ((allies.length < nearbyEnemies.length + outNumMargin)
				&& (myLoc.distanceSquaredTo(hqLoc) > 25)) {
			enemiesClose = true;

			// find closest allied robot. attract to that robot.
			if (allies.length > 0) {
				MapLocation closestAlly = findClosestRobot(allies);
				goalLoc = goalLoc.add(myLoc.directionTo(closestAlly),
						defAlliedAttraction);
			}
			// we need this check this because the array could still be null
			if (nearbyEnemies.length > 0) {// avoid enemy
				MapLocation closestEnemy = findClosestRobot(nearbyEnemies);
				goalLoc = goalLoc.add(myLoc.directionTo(closestEnemy),
						defEnemyRepulsion);
			}
			// We out number enemies, clump up and ATTACK!
		} else if (allies.length >= (nearbyEnemies.length + outNumMargin)) {
			enemiesClose = true;
			if (allies.length > 0) {
				MapLocation closestAlly = findClosestRobot(allies);
				goalLoc = goalLoc.add(myLoc.directionTo(closestAlly),
						offAlliedAttraction);
			}
			if (nearbyEnemies.length > 0) {
				MapLocation closestEnemy = findClosestRobot(nearbyEnemies);
				goalLoc = goalLoc.add(myLoc.directionTo(closestEnemy),
						offNearEnemyAttraction);
			} else {// no nearby enemies; go toward far enemy
				enemiesClose = false;
				MapLocation closestEnemy = findClosestRobot(enemies);
				goalLoc = goalLoc.add(myLoc.directionTo(closestEnemy),
						offFarEnemyAttraction);
			}
		}
		
		// now use that direction
		Direction finalDir = myLoc.directionTo(goalLoc);
		// TODO figure out why this value is random, i don't think it should be.
		if (Math.random() < .1)
			finalDir = finalDir.rotateRight();

		simpleMove(finalDir, enemiesClose);
	}

	/**
	 * TODO figure out what this method does. i think it is to try and keep
	 * robots from going nuts out if they are to far away.
	 * 
	 * @param dSquared
	 * @return
	 */
	private static int targetWeight(int dSquared) {
		if (dSquared > 100) {
			return 5;
		} else if (dSquared > 9) {
			return 2;
		} else {
			return 1; // tried changing to 0 from 1, need to test effect.
		}
	}

	/**
	 * Movement method,
	 * 
	 * @param dir
	 *            direction we are trying to move in.
	 * @param enemiesClose
	 *            boolean to indicate enemies are close
	 * @throws GameActionException
	 */
	private static void simpleMove(Direction dir, boolean enemiesClose)
			throws GameActionException {
		/*
		 * try to capture an encampment check if we have enough power first. and
		 * that we are on an encampment and that we are NOT adjacent to the HQ.
		 * TODO we need to optimize this for when to capture generator,
		 * supplier, artillery ect. TODO figure out a way to use the encampments
		 * next to the HQ without blocking it off.
		 */
		if (rc.getTeamPower() > rc.senseCaptureCost()
				&& rc.senseEncampmentSquare(myLoc)
				&& !myLoc.isAdjacentTo(hqLoc) && !enemiesClose) {
			if (goodArtillery()) {
				rc.captureEncampment(RobotType.ARTILLERY);
			}

			// if we are low on power, build generator to get more.
			// TODO optimize the desired power level (300).
			else if (rc.getTeamPower() < 300) {
				rc.captureEncampment(RobotType.GENERATOR);
			} else {
				// else build supplier to spend more energy.
				rc.captureEncampment(RobotType.SUPPLIER);
			}

		} else {
			// see if we can move in any direction up to 90' off goal.
			int[] directionOffsets = { 0, 1, -1, 2, -2 };
			/*
			 * 0 straight, 1 Right 45', -1 Left 45', 2 Right 90', -2 Left 90'
			 */
			Direction lookingAtCurrently = null;
			lookAround: for (int d : directionOffsets) {
				lookingAtCurrently = Direction.values()[(dir.ordinal() + d + 8) % 8];
				// check if we can move the direction we are looking.
				if (rc.canMove(lookingAtCurrently)) {
					// Check if there is a mine.
					Team currentMine = rc.senseMine(myLoc
							.add(lookingAtCurrently));
					// If there is a mine and it's not ours...
					if (currentMine != null && currentMine != rc.getTeam()) {
						// If there arn't enemies close, move that way and deal
						// with mines.
						if (!enemiesClose) {
							moveOrDefuse(lookingAtCurrently);
							break lookAround;
						}
						//TODO fix this so we avoid mines when enemies are close
					} else {
						moveOrDefuse(lookingAtCurrently);
						break lookAround;
					}

				}
			}
		}
	}

	/**
	 * Method to defuse a mine if there is one
	 * 
	 * @param dir
	 *            direction we are thinking about moving
	 * @throws GameActionException
	 */
	// TODO do we need to check if we do an area scan instead?
	// TODO accept mine info from simpleMove.
	private static void moveOrDefuse(Direction dir) throws GameActionException {
		MapLocation ahead = myLoc.add(dir);
		Team mineAhead = rc.senseMine(ahead);
		if ((mineAhead != null) && (mineAhead != rc.getTeam())) {
			rc.defuseMine(ahead);
		} else {
			rc.move(dir);
		}
	}

	/**
	 * FIND THE CLOSEST ROBOT
	 * 
	 * @param robots
	 *            array of robots to search through.
	 * @return the location of the closest robot.
	 * @throws GameActionException
	 */
	private static MapLocation findClosestRobot(Robot[] robots)
			throws GameActionException {
		int closestDist = 1000000;
		MapLocation closestEnemy = null;
		for (int i = robots.length; --i >= 0;) { // optimized for loop :D
			Robot arobot = robots[i];
			RobotInfo arobotInfo = rc.senseRobotInfo(arobot);
			int dist = arobotInfo.location.distanceSquaredTo(myLoc);
			if (dist < closestDist) {
				closestDist = dist;
				closestEnemy = arobotInfo.location;
			}
		}
		return closestEnemy;
	}

	/**
	 * FIND CLOSEST MAP LOCATION
	 * 
	 * @param locationsArr
	 *            array of map locations to search
	 * @return the closest location to the array.
	 * @throws GameActionException
	 */
	private static MapLocation findClosestMapLocation(MapLocation[] locationsArr)
			throws GameActionException {
		int closestDist = 1000000;
		MapLocation closestLoc = null;
		for (int i = locationsArr.length; --i >= 0;) { // optimized for loop.
														// save dem bytecodes.
			MapLocation aLocation = locationsArr[i];
			int dist = aLocation.distanceSquaredTo(myLoc);
			if (dist < closestDist) {
				closestDist = dist;
				closestLoc = aLocation;
			}
		}
		return closestLoc;
	}

	/**
	 * ARTILLERY PLACEMENT method determines if current location is good for
	 * artillery
	 * 
	 * @return boolean true if good spot for arty, false if not.
	 */
	private static boolean goodArtillery() {
		int y = hqLoc.y - myLoc.y;
		int x = hqLoc.x - myLoc.x;
		int slopeX = hqLoc.x - enemyHQLoc.x;
		int slopeY = hqLoc.y - enemyHQLoc.y;

		final int offset = 7; // determines how big of a swath of arty.

		for (int i = -offset; i < offset; i++) {
			if(slopeX == 0){
				//Divide by 0. 
				return true;
			}
			if ((y + i) == (slopeY / slopeX) * (x)) {
				return true;
			}
		}
		return false;
	}
	
/** ARTILLERY CODE
 *  code for artillery Robots. 
 * @throws GameActionException
 */
	private static void artilleryCode() throws GameActionException {
		while (true) {
			try {
				if(myLoc == null){
					myLoc = rc.getLocation();
				}
				if (rc.isActive()) {
					//TODO WE NEED TO STOP SHOOTING OURSELVES!!!!
					// All the enemies in range of the artillery.
					Robot[] enemies = rc.senseNearbyGameObjects(Robot.class,
							63, rc.getTeam().opponent());
					MapLocation closestEnemy;
					// if there are enemies in range...
					if (enemies.length > 0) {
						closestEnemy = findClosestRobot(enemies);
						if (rc.canAttackSquare(closestEnemy)) {
							rc.attackSquare(closestEnemy);
						}
					}
				
				}
			} catch (Exception e) {
				System.out.println("Artillery Exception");
				e.printStackTrace();
			}
			rc.yield();
		}
		
	}

	/**
	 * HQ CODE Method to run HQ code.
	 */
	private static void hqCode() {
		// initialize HQ and Enemy HQ locations
		if (hqLoc == null) {
			hqLoc = rc.senseHQLocation();
		}
		if (enemyHQLoc == null) {
			enemyHQLoc = rc.senseEnemyHQLocation();
		}
		// TODO is the below line necessary?
		MapLocation rallyPt = hqLoc.add(hqLoc.directionTo(enemyHQLoc), 5);
		Boolean attack = false;
		while (true) {
			try {

				if (rc.isActive()) {

					// Spawn a soldier
					// Robot[] alliedRobots =
					// rc.senseNearbyGameObjects(Robot.class,100000,rc.getTeam());
					// TODO: do we always want to spawn a soldier?
					if (rc.getTeamPower() - 40 > 100) {
						// 0: straight, 1: Right 45', -1: Left 45', 2:Right 90',
						// -2:Left 90'

						int[] directionOffsets = { 0, 1, -1, 2, -2 };

						lookAround: for (Direction d : Direction.values()) {
							Direction lookingAtCurrently = null;
							for (int di : directionOffsets) {
								// Doesn't this make d=(0 through 4)?
								lookingAtCurrently = Direction.values()[(d
										.ordinal() + di + 8) % 8];

								if (rc.canMove(lookingAtCurrently)) {
									rc.spawn(lookingAtCurrently);
									break lookAround;
								}
							}
						}
						/*
						 * TODO: Optimize upgrades. which ones we want, when we
						 * want them. which map sizes do we want them on? right
						 * now we only research if we are out of energy. else if
						 * (!rc.hasUpgrade(Upgrade.PICKAXE)) {
						 * rc.researchUpgrade(Upgrade.PICKAXE); }
						 */
					} else if (!rc.hasUpgrade(Upgrade.FUSION)) {
						// reduces stored energy decay, good macro
						rc.researchUpgrade(Upgrade.FUSION);
					} else if (!rc.hasUpgrade(Upgrade.DEFUSION)) {
						// reduces mine defusal time.
						rc.researchUpgrade(Upgrade.DEFUSION);
					} else {
						// DON'T NEED TO CHECK; IF WE HAVE IT, WE WON.
						rc.researchUpgrade(Upgrade.NUKE);
					}

					if (encampments == null) {
						// Initialize the encampments array.
						encampments = rc.senseAllEncampmentSquares();
					}

					/*
					 * move the rally point if it is a captured encampment TODO
					 * optimize encampment captures. Distance? Location (in
					 * enemy path)?
					 */
					MapLocation[] alliedEncampments = rc
							.senseAlliedEncampmentSquares();
					if (alliedEncampments.length > 0
							&& among(alliedEncampments, rallyPt)) {
						MapLocation closestEncampment = captureEncampments(
								encampments, alliedEncampments);
						if (closestEncampment != null) {
							rallyPt = closestEncampment;
						}
					} else if (alliedEncampments.length == 0) {
						MapLocation closestEncampment = captureEncampments(encampments);
						if (closestEncampment != null) {
							rallyPt = closestEncampment;
						}
					}
					// kill enemy if nearing round limit or injured
					// TODO optimize attacks so we group and attack in waves.
					if (rc.getEnergon() < 300 || Clock.getRoundNum() > 1500
							|| rc.senseEnemyNukeHalfDone() || attack) {
						rallyPt = enemyHQLoc;
					}

					if (Clock.getRoundNum()
							/ (hqLoc.distanceSquaredTo(enemyHQLoc) * 4) > 1) {

						rallyPt = hqLoc.add(hqLoc.directionTo(enemyHQLoc),
								(int) (Math.sqrt(hqLoc
										.distanceSquaredTo(enemyHQLoc)) * .5));
					}

					// message allies about where to go
					// TODO Should we only update if info changes?
					// what about bad info?
					int channel = getChannel();
					int msg = MapLocationToInt(rallyPt);
					rc.broadcast(channel, msg);
					rc.setIndicatorString(0, "Posted " + msg + " to " + channel);

					// message allies about whether to mine
					// TODO change mining behavior if we have pickaxe
					if (/* rc.hasUpgrade(Upgrade.PICKAXE) */rc
							.senseNearbyGameObjects(Robot.class, 1000000, rc
									.getTeam().opponent()).length < 3) {
						rc.broadcast(getChannel() + 1, 2);
					}
					// send along how many encampments we have
					rc.broadcast(channel + 2, alliedEncampments.length);
				}

			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}
			rc.yield();
		}
	}

	// Messaging functions
	/**
	 * Scrambling method to scramble the channel.
	 * 
	 * @return int, the channel we are using this round.
	 */
	public static int getChannel() {
		int clock = Clock.getRoundNum();
		int channel = (clock * mult + 6) % GameConstants.BROADCAST_MAX_CHANNELS;
		return channel;
	}

	/**
	 * Converts a mapLocation into an int for sending messages
	 * 
	 * @param loc
	 *            MapLocation to broadcast
	 * @return int representing that location TODO: add some 'password' so we
	 *         know the data is good.
	 */
	public static int MapLocationToInt(MapLocation loc) {
		return (loc.x * 1000) + loc.y;
	}

	/**
	 * Converts an int into a mapLocation
	 * 
	 * @param mint
	 *            the int from the message
	 * @return MapLocation from the message int. TODO: check somehow so we know
	 *         data is good.
	 */
	public static MapLocation IntToMaplocation(int mint) {
		int y = mint % 1000;
		int x = (mint - y) / 1000;

		if (x == 0 && y == 0) {
			return null;
		} else {
			return new MapLocation(x, y);
		}
	}

	// locating encampment
	// TODO: this method works, but it's ugly as a troll.
	public static MapLocation captureEncampments(MapLocation[] allEncampments,
			MapLocation[] alliedEncampments) throws GameActionException {

		// locate uncaptured encampments within a certain radius
		MapLocation[] neutralEncampments = new MapLocation[allEncampments.length];
		int neInd = 0;

		// Compute nearest encampment (counting the enemy HQ)
		outer: for (MapLocation enc : allEncampments) {
			for (MapLocation aenc : alliedEncampments)
				if (aenc.equals(enc))
					continue outer;
			if (hqLoc.distanceSquaredTo(enc) <= Math.pow(
					Clock.getRoundNum() / 10, 2)) {
				// add to neutral encampments list
				neutralEncampments[neInd] = enc;
				neInd = neInd + 1;
			}
		}
		rc.setIndicatorString(2,
				"neutral enc det " + neInd + " round " + Clock.getRoundNum());

		if (neInd > 0) {
			// proceed to an encampment and capture it
			int which = (int) ((Math.random() * 100) % neInd);
			MapLocation campLoc = neutralEncampments[which];
			return campLoc;
		} else {// no encampments to capture; change state
			return null;
		}
	}

	// Method to find closest encamp when we haven't captured any.
	public static MapLocation captureEncampments(MapLocation[] encampments)
			throws GameActionException {
		// locate uncaptured encampments within a certain radius
		MapLocation closestFreeEncamp = null;
		int shortestDist = 100000000;
		int dist;

		// Compute nearest encampment (counting the enemy HQ)
		for (MapLocation encamp : encampments) {

			dist = hqLoc.distanceSquaredTo(encamp);
			if (dist < shortestDist) {
				shortestDist = dist;
				closestFreeEncamp = encamp;
			}

		}

		if (shortestDist < 10000000) {
			// proceed to an encampment and capture it
			return closestFreeEncamp;
		} else {// no encampments to capture; change state
			return null;
		}
	}

	/**
	 * CHECK IF WE HAVE THIS ENCAMPMENT
	 * 
	 * @param alliedEncampments
	 *            all encampments we currently have.
	 * @param encampInQuestion
	 *            the encampment we are checking
	 * @return boolean true if we have it, false if we don't.
	 */
	private static boolean among(MapLocation[] alliedEncampments,
			MapLocation encampInQuestion) {
		for (MapLocation enc : alliedEncampments) {
			if (enc.equals(encampInQuestion))
				return true;
		}
		return false;
	}
}
