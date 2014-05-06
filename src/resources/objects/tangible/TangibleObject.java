/*******************************************************************************
 * Copyright (c) 2013 <Project SWG>
 * 
 * This File is part of NGECore2.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Using NGEngine to work with NGECore2 is making a combined work based on NGEngine. 
 * Therefore all terms and conditions of the GNU Lesser General Public License cover the combination.
 ******************************************************************************/
package resources.objects.tangible;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import main.NGECore;
import protocol.swg.ObjControllerMessage;
import protocol.swg.PlayClientEffectObjectMessage;
import protocol.swg.StopClientEffectObjectByLabel;
import protocol.swg.UpdatePVPStatusMessage;
import protocol.swg.objectControllerObjects.ShowFlyText;
import resources.common.OutOfBand;
import resources.common.RGB;
import resources.datatables.Options;
import resources.datatables.PvpStatus;
import resources.loot.LootGroup;
import resources.objects.ObjectMessageBuilder;
import resources.objects.creature.CreatureObject;
import resources.visitors.IDManagerVisitor;

import com.sleepycat.persist.model.NotPersistent;
import com.sleepycat.persist.model.Persistent;

import engine.clientdata.ClientFileManager;
import engine.clientdata.StfTable;
import engine.clients.Client;
import engine.resources.common.CRC;
import engine.resources.objects.SWGObject;
import engine.resources.scene.Planet;
import engine.resources.scene.Point3D;
import engine.resources.scene.Quaternion;

@Persistent(version=12)
public class TangibleObject extends SWGObject implements Serializable {
	
	private static final long serialVersionUID = 1L;
	// TODO: Thread safety
	
	protected int incapTimer = 10;
	private int conditionDamage = 0;
	protected int pvpBitmask = 0;
	protected byte[] customization;
	private List<Integer> componentCustomizations = new ArrayList<Integer>();
	private Map<String, Byte> customizationVariables = new HashMap<String, Byte>();
	protected int optionsBitmask = 0;
	private int uses = 0;
	private int maxDamage = 1000;
	private boolean staticObject = true;
	protected String faction = ""; // Says you're "Imperial Special Forces" if it's 0 for some reason
	protected int factionStatus = 0;
	@NotPersistent
	protected transient Vector<TangibleObject> defendersList = new Vector<TangibleObject>();	// unused in packets but useful for the server
	@NotPersistent
	private transient TangibleMessageBuilder messageBuilder;
	
	private int respawnTime = 0;
	private Point3D spawnCoordinates = new Point3D(0, 0, 0);
	
	//private TreeSet<TreeMap<String,Integer>> lootSpecification = new TreeSet<TreeMap<String,Integer>>();
	private List<LootGroup> lootGroups = new ArrayList<LootGroup>();
	
	@NotPersistent
	private transient boolean looted = false; // These 4 should not need to be persisted, since a looted corpse will get wiped with server restart	
	@NotPersistent
	private transient boolean lootLock = false;	
	@NotPersistent
	private transient boolean creditRelieved = false;	
	@NotPersistent
	private transient boolean lootItem = false;
	
	private boolean stackable = false;
	private int stackCount = 1;
	private boolean noSell = false;
	private byte junkType = -1;
	private int junkDealerPrice = 0;
	
	private String serialNumber;
	
	@NotPersistent
	private transient TangibleObject killer = null;
	
	public TangibleObject(long objectID, Planet planet, String template) {
		super(objectID, planet, new Point3D(0, 0, 0), new Quaternion(1, 0, 1, 0), template);
		messageBuilder = new TangibleMessageBuilder(this);
		if (this.getClass().getSimpleName().equals("TangibleObject")) setIntAttribute("volume", 1);
	}
	
	public TangibleObject(long objectID, Planet planet, String template, Point3D position, Quaternion orientation) {
		super(objectID, planet, position, orientation, template);
		messageBuilder = new TangibleMessageBuilder(this);
		spawnCoordinates = position.clone();
		if (this.getClass().getSimpleName().equals("TangibleObject")) setIntAttribute("volume", 1);
	}
	
	public TangibleObject() {
		super();
		messageBuilder = new TangibleMessageBuilder(this);
	}
	
	@Override
	public void initAfterDBLoad() {
		super.init();
		defendersList = new Vector<TangibleObject>();
		messageBuilder = new TangibleMessageBuilder(this);
	}
	
	public void setCustomName2(String customName) {
		setCustomName(customName);
		
		notifyObservers(messageBuilder.buildCustomNameDelta(customName), true);
	}

	public int getIncapTimer() {
		return incapTimer;
	}

	public void setIncapTimer(int incapTimer) {
		this.incapTimer = incapTimer;
	}
	
	public int getUses() {
		return uses;
	}
	
	public void setUses(int uses) {
		this.uses = uses;
		setIntAttribute("uses", uses);
	}

	public synchronized int getConditionDamage() {
		return conditionDamage;
	}

	public synchronized void setConditionDamage(int conditionDamage) {
		if(conditionDamage < 0)
			conditionDamage = 0;
		else if(conditionDamage > getMaxDamage())
			conditionDamage = getMaxDamage();
		this.conditionDamage = conditionDamage;
		notifyObservers(messageBuilder.buildConditionDamageDelta(conditionDamage), true);
		if (maxDamage > 0) {
			this.setStringAttribute("condition", (maxDamage + "/" + (maxDamage - conditionDamage)));
		}
	}

	public byte[] getCustomization() {
		return customization;
	}

	public void setCustomization(byte[] customization) {
		synchronized(objectMutex) {
			this.customization = customization;
		}
		
		notifyObservers(messageBuilder.buildCustomizationDelta(customization), true);
	}

	public List<Integer> getComponentCustomizations() {
		return componentCustomizations;
	}

	public void setComponentCustomizations(List<Integer> componentCustomizations) {
		this.componentCustomizations = componentCustomizations;
	}

	public int getOptionsBitmask() {
		return optionsBitmask;
	}

	public void setOptionsBitmask(int optionsBitmask) {
		this.optionsBitmask = optionsBitmask;
	}
	
	public void setOptions(int options, boolean add) {
		synchronized(objectMutex) {
			if (options != 0) {
				if (add) {
					addOption(options);
				} else {
					removeOption(options);
				}
			}
		}
	}
	
	public boolean getOption(int option) {
		synchronized(objectMutex) {
			return ((optionsBitmask & option) == option);
		}
	}
	
	public void addOption(int option) {
		setOptionsBitmask(getOptionsBitmask() | option);
	}
	
	public void removeOption(int option) {
		setOptionsBitmask(getOptionsBitmask() & ~option);
	}
	
	public int getMaxDamage() {
		return maxDamage;
	}

	public void setMaxDamage(int maxDamage) {
		this.maxDamage = maxDamage;
		
		this.setStringAttribute("condition", (maxDamage + "/" + (maxDamage - conditionDamage)));
	}

	public boolean isStaticObject() {
		return staticObject;
	}

	public void setStaticObject(boolean staticObject) {
		this.staticObject = staticObject;
	}
	
	public int getPvPBitmask() {
		synchronized(objectMutex) {
			return pvpBitmask;
		}
	}

	public void setPvPBitmask(int pvpBitmask) {
		synchronized(objectMutex) {
			this.pvpBitmask = pvpBitmask;
		}
	}
	
	public boolean getPvpStatus(int pvpStatus) {
		synchronized(objectMutex) {
			return ((pvpBitmask & pvpStatus) != 0);
		}
	}
	
	public void setPvpStatus(int pvpBitmask, boolean add) {
		synchronized(objectMutex) {
			if (pvpBitmask != 0) {
				if (add) {
					this.pvpBitmask |= pvpBitmask;
				} else {
					this.pvpBitmask &= ~pvpBitmask;
				}
			}
		}

		//updatePvpStatus();
	}
	
	public void updatePvpStatus() {
		HashSet<Client> observers = new HashSet<Client>(getObservers());
		
		for (Iterator<Client> it = observers.iterator(); it.hasNext();) {
			Client observer = it.next();
			
			if (observer.getParent() != null) {
				observer.getSession().write(new UpdatePVPStatusMessage(this.getObjectID(), NGECore.getInstance().factionService.calculatePvpStatus((CreatureObject) observer.getParent(), this), getFaction()).serialize());
				if(getClient() != null)
					getClient().getSession().write(new UpdatePVPStatusMessage(observer.getParent().getObjectID(), NGECore.getInstance().factionService.calculatePvpStatus((CreatureObject) this, (CreatureObject) observer.getParent()), getFaction()).serialize());
			}

		}
		
		if (getClient() != null) {
			CreatureObject companion = NGECore.getInstance().mountService.getCompanion((CreatureObject) this);
			
			if (companion != null) {
				companion.updatePvpStatus();
			}
		}
	}
	
	public Byte getCustomizationVariable(String type)
	{
		if(customizationVariables.containsKey(type)) return customizationVariables.get(type);
		System.err.println("Error: object doesn't have customization variable " + type);
		return null;
	}
	
	public void setCustomizationVariable(String type, byte value)
	{
		if(customizationVariables.containsKey(type)) customizationVariables.replace(type, value);
		else customizationVariables.put(type, value);
		
		notifyObservers(messageBuilder.buildCustomizationDelta(getCustomizationBytes()), true);
	}
	
	public void removeCustomizationVariable(String type)
	{
		if(customizationVariables.containsKey(type)) 
		{
			customizationVariables.remove(type);
			notifyObservers(messageBuilder.buildCustomizationDelta(getCustomizationBytes()), true);
		}
	}
	
	private byte[] getCustomizationBytes()
	{
		//if(customizationVariables.size() == 0) customization = { 0x00 };
		
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		try
		{
			IDManagerVisitor visitor = ClientFileManager.loadFile("customization/customization_id_manager.iff", IDManagerVisitor.class);	
			
			stream.write((byte)0x02); // Unk
			stream.write((byte)customizationVariables.size()); // Number of customization attributes
			
			for(String type : customizationVariables.keySet())
			{
				stream.write(visitor.getAttributeIndex(type)); // Index of palette type within "customization/customization_id_manager.iff"
				stream.write(customizationVariables.get(type)); // Value/Index within palette
				
				// Seperator/Footer
				stream.write((byte) 0xC3);
				stream.write((byte) 0xBF);
				stream.write((byte) 0x03);
			}
			return stream.toByteArray();
		}
		catch (Exception e) { e.printStackTrace(); }
		return null;	
	}
	
	public String getFaction() {
		synchronized(objectMutex) {
			return faction;
		}
	}
	
	public void setFaction(String faction) {
		synchronized(objectMutex) {
			this.faction = faction;
		}
		
		updatePvpStatus();
	}
	
	public int getFactionStatus() {
		synchronized(objectMutex) {
			return factionStatus;
		}
	}
	
	public void setFactionStatus(int factionStatus) {
		synchronized(objectMutex) {
			this.factionStatus = factionStatus;
		}
	}
	
	public Vector<TangibleObject> getDefendersList() {
	    synchronized(objectMutex) {
    			return defendersList;
    	    }	
	}
	
	public void addDefender(TangibleObject defender) {
				
		defendersList.add(defender);
		
		if(this instanceof CreatureObject) {
			CreatureObject creature = (CreatureObject) this;
			
			if(creature.getCombatFlag() == 0)
				creature.setCombatFlag((byte) 1);
		}
		
	}
	
	public void removeDefender(TangibleObject defender) {
		
		defendersList.remove(defender);
		
		if(this instanceof CreatureObject) {
			CreatureObject creature = (CreatureObject) this;
			
			if(creature.getCombatFlag() == 1 && defendersList.isEmpty())
				creature.setCombatFlag((byte) 0);
		}
		
	}
	
	public boolean isAttackableBy(CreatureObject attacker) {
		int pvpStatus = NGECore.getInstance().factionService.calculatePvpStatus(attacker, this);
		return (((pvpStatus & PvpStatus.Attackable) == PvpStatus.Attackable) || ((pvpStatus & PvpStatus.Aggressive) == PvpStatus.Aggressive));
		
		/*
		CreatureObject creature;
		
		if(this instanceof CreatureObject) {
			creature = (CreatureObject) this;
			if(creature.getDuelList().contains(attacker) && attacker.getDuelList().contains(this))
				return true;
		}
		
		if(faction.equals("rebel") && attacker.getFaction().equals("rebel"))
			return false;
		else if(faction.equals("imperial") && attacker.getFaction().equals("imperial"))
			return false;
		else if(attacker.getSlottedObject("ghost") != null) {
			
			if(this instanceof CreatureObject && getSlottedObject("ghost") != null) {
				
				creature = (CreatureObject) this;
				
				if(creature.getFactionStatus() == 2 && attacker.getFactionStatus() == 2)
					return true;
				else
					return false;
				
			}

			if((faction.equals("rebel") || faction.equals("imperial")) && attacker.getFactionStatus() >= 1)
				return true;
			else if((faction.equals("rebel") || faction.equals("imperial")) && attacker.getFactionStatus() == 0)
				return false;
			
			return getPvPBitmask() == 1 || getPvPBitmask() == 2;
			
		} else if(attacker.getSlottedObject("ghost") == null)
			return true;

		return getPvPBitmask() == 1 || getPvPBitmask() == 2;
		*/
	}
	
	public void showFlyText(OutOfBand outOfBand, float scale, RGB color, int displayType, boolean notifyObservers) {
		showFlyText("", outOfBand, scale, color, displayType, notifyObservers);
	}
	
	public void showFlyText(String stf, float scale, RGB color, int displayType, boolean notifyObservers) {
		showFlyText(stf, new OutOfBand(), scale, color, displayType, notifyObservers);
	}
	
	public void showFlyText(String stf, OutOfBand outOfBand, float scale, RGB color, int displayType, boolean notifyObservers) {
		if (outOfBand == null) {
			outOfBand = new OutOfBand();
		}
		
		if (color == null) {
			color = new RGB(255, 255, 255);
		}
		
		if (getClient() != null) {
			getClient().getSession().write((new ObjControllerMessage(0x0000000B, new ShowFlyText(getObjectID(), getObjectID(), stf, outOfBand, scale, color, displayType))).serialize());
		}
		
		if (notifyObservers) {
			Set<Client> observers = getObservers();
			
			for (Client client : observers) {
				client.getSession().write((new ObjControllerMessage(0x0000000B, new ShowFlyText(client.getParent().getObjectID(), getObjectID(), stf, outOfBand, scale, color, displayType))).serialize());
			}
		}
	}
	
	public void playEffectObject(String effectFile, String commandString) {
		notifyObservers(new PlayClientEffectObjectMessage(effectFile, getObjectID(), commandString), true);
	}
	
	public void stopEffectObject(String commandString) {
		notifyObservers(new StopClientEffectObjectByLabel(getObjectID(), commandString), true);
	}
	
	public int getRespawnTime() {
		synchronized(objectMutex) {
			return respawnTime;
		}
	}
	
	public void setRespawnTime(int respawnTime) {
		synchronized(objectMutex) {
			this.respawnTime = respawnTime;
		}
	}
	
	public TangibleObject getKiller() {
		synchronized(objectMutex) {
			return killer;
		}
	}
	
	public void setKiller(TangibleObject killer) {
		synchronized(objectMutex) {
			this.killer = killer;
		}
	}
	
	// Returns the full STF-based name filepath
	public String getProperName()
	{
		return  "@" + getStfFilename() + ":" + getStfName();
	}
	
	// Returns the STF-based description filepath
	public String getProperDescription()
	{
		return "@" + getDetailFilename() + ":" + getDetailName();
	}
	
	// Returns the current, true name of the Object
	public String getTrueName()
	{
		return getCustomName() != null ? getCustomName() : getTrueStfName();
	}
		
	// Returns the true STF-based name
	public String getTrueStfName()
	{
		String name = null;
		try
		{
			StfTable stf = new StfTable("clientdata/string/en/" + getStfFilename() + ".stf");
			for (int s = 1; s < stf.getRowCount(); s++) 
			{		
				if(stf.getStringById(s).getKey().equals(getStfName())) name = stf.getStringById(s).getValue();
			}
        } 
		catch (Exception e) { }
		
		return name;	
	}
	
	public List<LootGroup> getLootGroups() {
		return lootGroups;
	}

	public void addToLootGroups(String[] lootPoolNames, double[] lootPoolChances, double lootGroupChance) {
		System.out.println("lootPoolNames[0] " + lootPoolNames[0]);
		LootGroup lootGroup = new LootGroup(lootPoolNames, lootPoolChances, lootGroupChance);
		this.lootGroups.add(lootGroup);
	}
	
	public boolean isLooted() {
		return looted;
	}

	public void setLooted(boolean looted) {
		this.looted = looted;
	}
	
	public boolean isLootLock() {
		return lootLock;
	}

	public void setLootLock(boolean lootLock) {
		this.lootLock = lootLock;
	}
	
	public boolean isLootItem() {
		return lootItem;
	}

	public void setLootItem(boolean lootItem) {
		this.lootItem = lootItem;
	}
	
	public boolean isStackable() {
		return stackable;
	}

	public void setStackable(boolean stackable) {
		this.stackable = stackable;
	}
	
	public int getStackCount() {
		return stackCount;
	}

	public void setStackCount(int stackCount) {
		this.stackCount = stackCount;
	}
	
	public boolean isNoSell() {
		return noSell;
	}

	public void setNoSell(boolean noSell) {
		this.noSell = noSell;
	}
	
	public byte getJunkType() {
		return junkType;
	}

	public void setJunkType(byte junkType) {
		this.junkType = junkType;
	}

	public int getJunkDealerPrice() {
		return junkDealerPrice;
	}

	public void setJunkDealerPrice(int junkDealerPrice) {
		this.junkDealerPrice = junkDealerPrice;
	}
	
	public boolean isCreditRelieved() {
		return creditRelieved;
	}

	public void setCreditRelieved(boolean creditRelieved) {
		if (creditRelieved)
			this.creditRelieved = creditRelieved; // only allow one state change to prevent hacking
	}
	
	public String getSerialNumber() {
		return getStringAttribute("serial_number");
	}

	public void setSerialNumber(String serialNumber) {
		setStringAttribute("serial_number", serialNumber);
		setOptions(Options.SERIAL, true);
	}
	
	public void sendDelta3(Client destination) {
		destination.getSession().write(messageBuilder.buildDelta3());
		//tools.CharonPacketUtils.printAnalysis(messageBuilder.buildDelta3(),"TANO3 Delta");
	}
	
	public void sendAssemblyDelta3(Client destination) {
		destination.getSession().write(messageBuilder.buildAssemblyDelta3());
		//tools.CharonPacketUtils.printAnalysis(messageBuilder.buildAssemblyDelta3(),"TANO3 Assembly Delta");
	}
	
	public void sendCustomizationDelta3(Client destination, String enteredName){
		destination.getSession().write(messageBuilder.buildCustomNameDelta(enteredName));
		//tools.CharonPacketUtils.printAnalysis(messageBuilder.buildCustomNameDelta(enteredName),"TANO3 Customization Delta");
	}	
	
	
	@Override
	public void sendBaselines(Client destination) {


		if(destination == null || destination.getSession() == null) {
			System.out.println("NULL destination");
			return;
		}
		
		destination.getSession().write(messageBuilder.buildBaseline3());
		destination.getSession().write(messageBuilder.buildBaseline6());
		destination.getSession().write(messageBuilder.buildBaseline8());
		destination.getSession().write(messageBuilder.buildBaseline9());
		
		if(getPvPBitmask() != 0) {
			UpdatePVPStatusMessage upvpm = new UpdatePVPStatusMessage(getObjectID());
			upvpm.setFaction(UpdatePVPStatusMessage.factionCRC.Neutral);
			upvpm.setStatus(getPvPBitmask());
			destination.getSession().write(upvpm.serialize());
		}
		

	}
	
	public ObjectMessageBuilder getMessageBuilder() {
		return messageBuilder;
	}
	
}
