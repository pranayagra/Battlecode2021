## Map

* 32 x 32 to 64 x 64 grid. We do not know the absolute position of these coordinates (must find out through exploration)
* passability: [0.1, 1.0] -> impacted by swamp to slow down robot actions -> action causes $\frac{base \ cooldown \ value}{passability \ of \ current \ square}$. Robots can only perform actions when cooldown < 1. Each round, cooldown decreases by 1
* The map is symmetric by rotation or reflection -- we can use this to our advantage maybe 



## Influence (money type currency)

* Influence is not a global resource
* Influence is distributed among Enlightenment Centers & is generated passively both by ECs and by specific robots



## Votes

* Objective is to win the most votes (max 3000 rounds)
* Even if the opponent is winning in votes, if we destroy all the robots of the enemy before 3000 rounds, we will win. This may be a strategy (ignore votes, spend resources on army, and attack)



## Robots

* From passability, a robot can only perform an action if the current cooldown is less than 1. Each round the current cooldown decreases by 1. Performing an action adds $\frac{base \ cooldown \ value}{passability \ of \ current \ square}$.



## Unit Creation

* Create units by transferring part of your ECs influence to the new unit. Spending C influence on a type of robot will give its max and starting conviction (health)
* Conviction -> how loyal a unit is to your party; more influence results in greater conviction. Similar functionality to "health" of unit

## Building Creation

* Buildings are immobile (ECs are the only type)
* Rumored that devs in the future may change the rules to allow movement of ECs (not sure yet)

![image-20210104234359947](C:\Users\prana\AppData\Roaming\Typora\typora-user-images\image-20210104234359947.png)

* Note: Radius is calculated by distance-squared units (A square north 3 and east 4 away => $3^2 + 4^2$ = 25)



## Movement

* Units can move to an unoccupied adjacent tile (total 8) if the cooldown is less than 1



## Sensing the Environment

* Remember each robot is independent and runs it own version of the code (sharing information between robots is not trivial and is done through flags discussed later)
* All robots may **sense** the passability of nearby map squares and any robots located on them. Robots without true sense are unable to distinguish between slanderers and politicians (slanders appear as politicians to them)
* Muckrakers can **detect** the presence of robots (only getting location information) 40 radius away, but can sense 30 units away
* sense is more information than detect it seems like, and is only different for Muckrakers



## Robots: detail

### Politicians

* See enemy slanderers as politicians
* Politicians convert other robots to their cause or heal (give conviction) to your team.
* Each $n$ nearby robots (specifiable up to action radius) will receive $\frac{max(conviction - 10, 0)}{n}$ conviction (health)
  * Extra conviction not divisible by $n$ will be distributed based on earlier creation time.
  * Ally robots will be "healed" by this amount, capped by the max conviction that they have
  * Enemy or neutral robot will lose conviction. If the conviction becomes negative, then slanderers and muckrakers will be destroyed; politicians and buildings will be converted to your team.

### Slanderers

* Spread falsehoods, generating influence for the ECs that created it

* See enemy slanderers as politicians

* Embezzle (passive) -> After creation from specific EC

  * 1-50 turns, as long as the slanderer doesn't die and the EC remain friendly, the EC passively receives $\lfloor{0.05 * \text{slanderer influence}}\rfloor$ per round. Total ~ $2.5 * \text{slanderer influence}$, so net positive $1.5 * \text{slanderer influence}$

* Camouflage (passive) -> After creation

  * 300 rounds after, slanderer converts into a politician with equal conviction

    

* The slanderer can probably run back to the HQ base to stay safe behind a wall of politicians and have high influence so it doesn't die + generate a lot... we need to also protect the EC

* I have heard that the devs may nerf these since they generate too much right now...

### Muckrakers

* Search the map to expose enemy slanderers
* The muckrakers can sense very far away, but only perform action close
* Expose (active) ->
  * Targets an enemy slanderer, destroying it, and for the next 50 turns speeches by politicians will have a multiplicative factor of $1.01^{\text{slanderer's influence}}$ 

* I think these are broken ->
  * we can push a stream of muckrakers with a mixture of low conviction (waste expensive politician since they perform suicide upon active) to destroy slanderers
  * or high ones if all their politicians are low health (I don't think this is ever a net positive, smaller health muckrakers are better always I think)
  * Note that politicians are faster than muckrakers, so they may be able to generate before we can stream.. not sure
  * Even if this is successful, we need to have speeches/politicians on stand by to capitalize on the advantage

### ECs

* Cannot be built or moved. Used to create the other three type of units.

* ECs initially belonging to a team will have 150 influence, neutral ones will have 50-500

* The ECs conviction (health) is equal to the current influence

* Passive income generation for round $t$ => $f(t) = \lceil 0.2 \sqrt{t} \rceil$ 

* Bid (active) -> 

  * Each EC will perform a bid. The EC with the highest bid will win a vote for the respective team. The team that lost will lose half the value of their max bid (to prevent spamming bids?)
  * Note - at most 3000 rounds. If a team loses all its robots before 3000 rounds, then it immediately loses.
  * NOTE - I have seen other users say this may not actually be an active skill (since it does not forgo building potentially. It could be that creating units is not an active skill. Should test out in actual game to confirm)

  

* I think the move is to capture the neutral ECs ASAP and then do:

* 1) safe farm -> create weak politicians as meat shields so enemy attack is magnitudes less (split speech damage over $n$ items instead of $1$ item)

* 2) risky farm -> create slanderers

* 3) mid farm/attack -> create muckrakers and maybe a mixture of the others, and try to attack enemies slanderer assuming they created it



## Ways to win

* Get 1501 votes and then play survival
* Forget about votes and try to destroy enemy < 3k rounds



## Communication

* Each robot on the map has a flag (R G B) 24-bit integer that can be set for a bytecode cost and persists until changed again. A robot's flag is visible to ALL other robots that can sense it, even enemy robots.
* Question -> on round x you set some the flags, and then on x+1 you do communication? Is it possible to control the flow --> aka execute robots in a certain order so that the flag is set before a different robot is executed? Probably not

* Additionally, enlightenment centers can see the flags of all robots, and all robots can see the flags of all enlightenment centers.
  * Useful to communicate info to EC. 
  * I think for the second part you need to somehow figure out which flag belongs to which center (based on some hash) -- probably make flag some team hash + coordinate location + information

## Bytecode limits

* Politician: 6,000
* Slanderer: 3,000
* Muckraker: 9,000
* Enlightenment Center: 12,000

* I believe pathfinding on a 5x5 sub grid ~15k bytecode, so we need to be careful.
* The Clock class has some good information with seeing how much bytecode we have used already / remaining / yielding turn