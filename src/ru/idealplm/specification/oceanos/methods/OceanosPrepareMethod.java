package ru.idealplm.specification.oceanos.methods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import com.teamcenter.rac.aif.kernel.AIFComponentContext;
import com.teamcenter.rac.kernel.TCComponent;
import com.teamcenter.rac.kernel.TCComponentBOMLine;
import com.teamcenter.rac.kernel.TCComponentBOMView;
import com.teamcenter.rac.kernel.TCComponentBOMViewRevision;
import com.teamcenter.rac.kernel.TCComponentBOMWindow;
import com.teamcenter.rac.kernel.TCComponentBOMWindowType;
import com.teamcenter.rac.kernel.TCComponentItem;
import com.teamcenter.rac.kernel.TCComponentItemRevision;
import com.teamcenter.rac.kernel.TCComponentRevisionRule;
import com.teamcenter.rac.kernel.TCComponentRevisionRuleType;
import com.teamcenter.rac.kernel.TCException;
import com.teamcenter.rac.kernel.TCSession;

import ru.idealplm.specification.blockline.BlockLine;
import ru.idealplm.specification.core.Block;
import ru.idealplm.specification.core.Error;
import ru.idealplm.specification.core.Specification;
import ru.idealplm.specification.core.Specification.BlockContentType;
import ru.idealplm.specification.methods.IPrepareMethod;
import ru.idealplm.specification.oceanos.comparators.PositionComparator;

public class OceanosPrepareMethod implements IPrepareMethod
{	
	private Specification specification;
	private HashMap<String,String> prevPosMap = new HashMap<String,String>();
	private ArrayList<BlockLine> postAddMat = new ArrayList<BlockLine>();
	private HashMap<String,String> lengthCutToPosMap = new HashMap<String, String>();

	@Override
	public void prepareData()
	{
		try{
			this.specification = Specification.getInstance();
			System.out.println("...METHOD...  PrepareMethod");
			
			for(Block block:specification.getBlockList()) {
				// ��� ������� ��� ���� ���
				if(block.blockContentType==BlockContentType.MATERIALS){
					for(BlockLine l:block.getListOfLines()){
						for(BlockLine attached:l.getAttachedLines()){
							postAddMat.add(attached);
						}
						l.attachedLines.clear();
					}
					block.getListOfLines().addAll(postAddMat);
				}
				// ��� ��������� � ������ ������� ������� ��������� ����� �� ����� �������, ��� � � ��������� � ����������� ������� ������� (���� ����� ��� ��)
				for(BlockLine bl:block.getListOfLines()){
					if(!bl.isSubstitute){
						if(bl.getRefBOMLines()!=null && !bl.isSubstitute){
							for(TCComponentBOMLine chbl:bl.getRefBOMLines()){
								try {
									chbl.setProperty("bl_sequence_no", bl.attributes.getPosition());
								} catch (TCException e) {
									e.printStackTrace();
									if(block.blockContentType==BlockContentType.MATERIALS) System.out.println("-> setting seq no " + bl.attributes.getPosition() + " for " + bl.attributes.getName() + " failed");
								}
								//TODO chbl.setProperty("Oc9_DisChangeFindNo", "true"); for mvm
							}
						}
					}
				}
				block.sort(false);
			}
			
			if(Specification.settings.getBooleanProperty("doReadLastRevPos"))
			{
				System.out.println("...READING LAST REV");
				clearAllPositions();
				TCComponentItemRevision prevRev = null;
				TCComponentItem topItem = Specification.getInstance().getTopBOMLine().getItem();
				TCComponentItemRevision topItemR = Specification.getInstance().getTopBOMLine().getItemRevision();
				TCComponent[] revisions = topItem.getRelatedComponents("revision_list");
				
				for(int i = 0; i < revisions.length; i++){
					if(revisions[i].getUid().equals(topItemR.getUid()) && i>0){
						prevRev = (TCComponentItemRevision) revisions[i-1];
						break;
					}
				}
				readDataFromPrevRev(prevRev);
				for(Block block:specification.getBlockList()) {
					if(!block.isRenumerizable) continue;
					for(BlockLine bl:block.getListOfLines()){
						if(!bl.isSubstitute){
							String currentPos =null;
							if(bl.blockContentType==BlockContentType.MATERIALS){
								currentPos = prevPosMap.get(bl.uid + bl.getProperty("SE Cut Length"));
							} else {
								currentPos = prevPosMap.get(bl.uid);
							}
							if(currentPos==null) currentPos = "";
							try {
								bl.attributes.setPosition(currentPos);
								if(bl.getRefBOMLines()!=null && !bl.isSubstitute){
									for(TCComponentBOMLine chbl:bl.getRefBOMLines()){
										chbl.setProperty("bl_sequence_no", currentPos);
										//TODO chbl.setProperty("Oc9_DisChangeFindNo", "true"); for mvm
									}
								}
								if(bl.getSubstituteBlockLines()!=null){
									for(BlockLine sbl:bl.getSubstituteBlockLines()){
										sbl.attributes.setPosition(currentPos+"*");
									}
								}
							} catch (TCException e) {
								e.printStackTrace();
							}
						}
					}
					block.sort(false);
				}
			}
			
			if(Specification.settings.getBooleanProperty("doRenumerize"))
			{
				System.out.println("...RENUMERIZING");
				String currentPos = "1"; // 
				clearAllPositions();
				
				for(Block block:specification.getBlockList()) {
					if(!block.isRenumerizable) continue;
					for(BlockLine bl:block.getListOfLines()){
						if(!bl.isSubstitute){
							
								if(bl.blockContentType==BlockContentType.MATERIALS && lengthCutToPosMap.containsKey(bl.uid+bl.getProperty("SE Cut Length"))){
									bl.attributes.setPosition(lengthCutToPosMap.get(bl.uid+bl.getProperty("SE Cut Length")));
									for(TCComponentBOMLine chbl:bl.getRefBOMLines()){
										try{
											chbl.setProperty("bl_sequence_no", lengthCutToPosMap.get(bl.uid+bl.getProperty("SE Cut Length")));
										}catch(Exception ex){
											ex.printStackTrace();
										}
									}
									continue;
								} else {
									if(bl.blockContentType==BlockContentType.MATERIALS && !bl.getProperty("SE Cut Length").isEmpty()){
										lengthCutToPosMap.put(bl.uid+bl.getProperty("SE Cut Length"), currentPos);
									}
									bl.attributes.setPosition(currentPos);
									for(TCComponentBOMLine chbl:bl.getRefBOMLines()){
										try{
											chbl.setProperty("bl_sequence_no", bl.attributes.getPosition());
										}catch(Exception ex){
											ex.printStackTrace();
										}
									}
								}
								
								if(bl.getRefBOMLines()!=null && !bl.isSubstitute){
									for(TCComponentBOMLine chbl:bl.getRefBOMLines()){
										try{
											chbl.setProperty("bl_sequence_no", bl.attributes.getPosition());
										}catch(Exception ex){
											ex.printStackTrace();
										}
									}
								}
								
								if(bl.getSubstituteBlockLines()!=null){
									for(BlockLine sbl:bl.getSubstituteBlockLines()){
										sbl.attributes.setPosition(currentPos+"*");
									}
								}
								currentPos = String.valueOf(Integer.valueOf(currentPos)+1 + block.intervalPosNum);
								
						}
					}
					currentPos = String.valueOf(Integer.valueOf(currentPos) + block.reservePosNum);
					block.sort(false);
				}
			}
			
			if(Specification.settings.getBooleanProperty("doUseReservePos"))
			{
				System.out.println("...USING RESERVE POS");
				int currentPos = 1;
				for(Block block:specification.getBlockList()) {
					if(!block.isRenumerizable) continue;
					for(BlockLine bl:block.getListOfLines()){
						if(!bl.isSubstitute){
							if(!bl.isRenumerizable){
								currentPos = Integer.parseInt(bl.attributes.getPosition()) + 1;
								continue;
							} else {
								int nextPos = -1;
								int indexOfLine = block.getListOfLines().indexOf(bl);
								if(indexOfLine <= block.size()-1){
									nextPos = Integer.parseInt(block.getListOfLines().get(indexOfLine+1).attributes.getPosition());
								} else {
									int indexOfBlock = specification.getBlockList().indexOf(block);
									if(indexOfBlock < specification.getBlockList().size()){
										nextPos = Integer.parseInt(specification.getBlockList().get(indexOfBlock+1).getListOfLines().get(0).attributes.getPosition());
									}
								}
								if(nextPos!=-1){
									if(currentPos>=nextPos){
										Specification.errorList.addError(new Error("ERROR", "���������� ��������� ������� ��� " + bl.attributes.getId()));
									}
								}
							}
							try {
								//bl.renumerize(String.valueOf(currentPos));
								bl.attributes.setPosition(String.valueOf(currentPos));
								if(bl.getRefBOMLines()!=null && !bl.isSubstitute){
									for(TCComponentBOMLine chbl:bl.getRefBOMLines()){
										chbl.setProperty("bl_sequence_no", String.valueOf(currentPos));
									}
								}
								if(bl.getSubstituteBlockLines()!=null){
									for(BlockLine sbl:bl.getSubstituteBlockLines()){
										sbl.attributes.setPosition(currentPos+"*");
									}
								}
							} catch (TCException e) {
								e.printStackTrace();
							}
							currentPos = currentPos + block.intervalPosNum + 1;
						}
					}
					currentPos+=block.reservePosNum;
					Collections.sort(block.getListOfLines(), new PositionComparator());
				}
			}
			
			AIFComponentContext[] childBOMLines = Specification.getInstance().getTopBOMLine().getChildren();
			
			for (AIFComponentContext currBOMLine : childBOMLines) {
				TCComponentBOMLine bl = (TCComponentBOMLine) currBOMLine.getComponent();
				bl.pack();
			}
			Specification.getInstance().getTopBOMLine().refresh();
			
			//specification.getBlockList().getBlock(BlockContentType.DOCS, "Default").run();
	
		} catch (Exception ex){
			ex.printStackTrace();
		}
	}
	
	private void clearAllPositions()
	{
		try{
			for (AIFComponentContext currBOMLine : Specification.getInstance().getTopBOMLine().getChildren()) {
				TCComponentBOMLine bl = (TCComponentBOMLine) currBOMLine.getComponent();
				bl.pack();
				bl.refresh();
			}
			Specification.getInstance().getTopBOMLine().refresh();
		} catch (Exception ex){
			ex.printStackTrace();
		}
		
		for(Block block:specification.getBlockList()) {
			if(!block.isRenumerizable) continue;
			block.sort(true);
			for(BlockLine bl:block.getListOfLines()){
				if(!bl.isSubstitute){
					for(TCComponentBOMLine chbl:bl.getRefBOMLines()){
						try{
							chbl.setProperty("bl_sequence_no", "");
						}catch(Exception ex){
							ex.printStackTrace();
						}
					}
				}
			}
		}
		
		try{
			for (AIFComponentContext currBOMLine : Specification.getInstance().getTopBOMLine().getChildren()) {
				TCComponentBOMLine bl = (TCComponentBOMLine) currBOMLine.getComponent();
				if (bl.isPacked()) {
					bl.unpack();
					bl.refresh();
				}
			}
			Specification.getInstance().getTopBOMLine().refresh();
		} catch (Exception ex){
			ex.printStackTrace();
		}
	}
	
	void readDataFromPrevRev(TCComponentItemRevision rev)
	{
		try{
			TCComponentBOMLine topBOMLine = getBOMLine(rev, "view", "Released", Specification.session);
			AIFComponentContext[] childBOMLines = topBOMLine.getChildren();
			
			for (AIFComponentContext currBOMLine : childBOMLines) {
				TCComponentBOMLine bl = (TCComponentBOMLine) currBOMLine.getComponent();
				if(bl.getItem().getType().equals("Oc9_Material")){
					prevPosMap.put(bl.getItemRevision().getUid() + bl.getProperty("SE Cut Length"), bl.getProperty("bl_sequence_no"));
				} else {
					prevPosMap.put(bl.getItemRevision().getUid(), bl.getProperty("bl_sequence_no"));
				}
			}
		} catch (Exception ex){
			ex.printStackTrace();
		}
	}
	
	public TCComponentBOMLine getBOMLine(TCComponentItemRevision rev, String bvrType, String revisionRule, TCSession session) throws Exception
	{
		TCComponentBOMViewRevision bomViewRevision = null;
		TCComponentBOMWindow bomWindow = null;
		TCComponentRevisionRule revRule = null;
		bomViewRevision = getBomViewRevByType(rev, bvrType);
		if(revisionRule.equals("")){
			revRule = null;
		} else {
			revRule = (TCComponentRevisionRule)getRevRule(session, revisionRule);
		}
		bomWindow = createBomWindow(session, revRule);
		TCComponentBOMView bomView = getBomView(rev.getItem(), bvrType);
		TCComponentBOMLine bomLine = bomWindow.setWindowTopLine(rev.getItem(), rev, bomView, bomViewRevision);
		return bomLine;
	}
			        
	public TCComponentBOMViewRevision getBomViewRevByType(TCComponentItemRevision rev, String type)
	{
		try{
			TCComponent[] structureRevisions = rev.getRelatedComponents("structure_revisions");
			if(structureRevisions!=null){
				return (TCComponentBOMViewRevision)structureRevisions[0];
			} else {
				return null;
			}
		} catch(Exception ex){
			ex.printStackTrace();
			return null;
		}
	}
			        
	public TCComponent getRevRule(TCSession session, String rule)
	{
		try{
			TCComponentRevisionRuleType rrType = (TCComponentRevisionRuleType)session.getTypeComponent("RevisionRule");
			TCComponent[] rrComponents = rrType.extent();
			for(TCComponent rrComponent:rrComponents){
				if(rrComponent.toString().equalsIgnoreCase(rule)){
					return rrComponent;
			    }
			}
			return null;
		} catch (Exception ex){
			ex.printStackTrace();
			return null;
		}
	}
			        
	public TCComponentBOMWindow createBomWindow(TCSession session, TCComponentRevisionRule rule)
	{
		try{
			TCComponentBOMWindowType bwType = (TCComponentBOMWindowType)session.getTypeComponent("BOMWindow");
			return bwType.create(rule);
		} catch (Exception ex){
			ex.printStackTrace();
			return null;
		}
	}
			        
	public TCComponentBOMView getBomView(TCComponentItem item, String type)
	{
		try{
			TCComponent[] components = item.getRelatedComponents("bom_view_tags");
			if(components!=null){
				return (TCComponentBOMView)components[0];
			}
			return null;
		} catch (Exception ex){
			ex.printStackTrace();
			return null;
		}
	}
	}


