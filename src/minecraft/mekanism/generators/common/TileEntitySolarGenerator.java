package mekanism.generators.common;

import ic2.api.ElectricItem;
import ic2.api.IElectricItem;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.generators.common.BlockGenerator.GeneratorType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraftforge.common.ForgeDirection;
import universalelectricity.core.implement.IItemElectric;

import com.google.common.io.ByteArrayDataInput;

import dan200.computer.api.IComputerAccess;

public class TileEntitySolarGenerator extends TileEntityGenerator
{
	/** Whether or not this generator sees the sun. */
	public boolean seesSun = false;
	
	/** How fast this tile entity generates energy. */
	public int GENERATION_RATE;
	
	public TileEntitySolarGenerator()
	{
		super("Solar Generator", 96000, 60);
		GENERATION_RATE = 50;
		inventory = new ItemStack[1];
	}
	
	public TileEntitySolarGenerator(String name, int maxEnergy, int output, int generation)
	{
		super(name, maxEnergy, output);
		GENERATION_RATE = generation;
		inventory = new ItemStack[1];
	}
	
	@Override
	public int getSizeInventorySide(ForgeDirection side)
	{
		return 1;
	}
	
	@Override
	public boolean canSetFacing(int facing)
	{
		return facing != 0 && facing != 1;
	}
	
	@Override
	public void onUpdate()
	{
		super.onUpdate();
		
		if(inventory[0] != null && electricityStored > 0)
		{
			if(inventory[0].getItem() instanceof IItemElectric)
			{
				IItemElectric electricItem = (IItemElectric)inventory[0].getItem();
				double sendingElectricity = 0;
				double actualSendingElectricity = 0;
				double rejectedElectricity = 0;
				double itemElectricityNeeded = electricItem.getMaxJoules(inventory[0]) - electricItem.getJoules(inventory[0]);
				
				if(electricItem.getVoltage() <= electricityStored)
				{
					sendingElectricity = electricItem.getVoltage();
				}
				else if(electricItem.getVoltage() > electricityStored)
				{
					sendingElectricity = electricityStored;
				}
				
				if(sendingElectricity <= itemElectricityNeeded)
				{
					actualSendingElectricity = sendingElectricity;
				}
				else if(sendingElectricity > itemElectricityNeeded)
				{
					rejectedElectricity = sendingElectricity-itemElectricityNeeded;
					actualSendingElectricity = itemElectricityNeeded;
				}
				
				electricItem.setJoules((electricItem.getJoules(inventory[0]) + actualSendingElectricity), inventory[0]);
				setJoules(electricityStored - (actualSendingElectricity - rejectedElectricity));
			}
			else if(inventory[0].getItem() instanceof IElectricItem)
			{
				double sent = ElectricItem.charge(inventory[0], (int)(electricityStored*Mekanism.TO_IC2), 3, false, false)*Mekanism.FROM_IC2;
				setJoules(electricityStored - sent);
			}
		}
		
		if(!worldObj.isRemote)
		{
			if(worldObj.isDaytime() && !worldObj.isRaining() && !worldObj.isThundering() && !worldObj.provider.hasNoSky && worldObj.canBlockSeeTheSky(xCoord, yCoord+1, zCoord))
			{
				seesSun = true;
			}
			else {
				seesSun = false;
			}
			
			if(worldObj.getBlockId(xCoord, yCoord+1, zCoord) == MekanismGenerators.generatorID && worldObj.getBlockMetadata(xCoord, yCoord+1, zCoord) == GeneratorType.SOLAR_GENERATOR.meta)
			{
				seesSun = false;
			}
		}
		
		if(canOperate())
		{
			setJoules(electricityStored + getEnvironmentBoost());
		}
	}
	
	@Override
	public boolean canOperate()
	{
		return electricityStored < MAX_ELECTRICITY && seesSun;
	}
	
	@Override
	public int getEnvironmentBoost()
	{
		return seesSun ? GENERATION_RATE : 0;
	}

	@Override
	public String[] getMethodNames() 
	{
		return new String[] {"getStored", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getSeesSun"};
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, int method, Object[] arguments) throws Exception 
	{
		switch(method)
		{
			case 0:
				return new Object[] {electricityStored};
			case 1:
				return new Object[] {output};
			case 2:
				return new Object[] {MAX_ELECTRICITY};
			case 3:
				return new Object[] {(MAX_ELECTRICITY-electricityStored)};
			case 4:
				return new Object[] {seesSun};
			default:
				System.err.println("[Mekanism] Attempted to call unknown method with computer ID " + computer.getID());
				return null;
		}
	}

	@Override
	public void handlePacketData(INetworkManager network, Packet250CustomPayload packet, EntityPlayer player, ByteArrayDataInput dataStream)
	{
		try {
			facing = dataStream.readInt();
			electricityStored = dataStream.readDouble();
			isActive = dataStream.readBoolean();
			seesSun = dataStream.readBoolean();
			worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord);
		} catch (Exception e)
		{
			System.out.println("[Mekanism] Error while handling tile entity packet.");
			e.printStackTrace();
		}
	}

	@Override
	public void sendPacket()
	{
		PacketHandler.sendTileEntityPacketToClients(this, 0, facing, electricityStored, isActive, seesSun);
	}

	@Override
	public void sendPacketWithRange() 
	{
		PacketHandler.sendTileEntityPacketToClients(this, 50, facing, electricityStored, isActive, seesSun);
	}
}