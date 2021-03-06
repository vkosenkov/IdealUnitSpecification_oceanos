package ru.idealplm.specification.oceanos.handlers;

import com.teamcenter.rac.aif.kernel.AIFComponentContext;
import com.teamcenter.rac.kernel.TCComponent;
import com.teamcenter.rac.kernel.TCComponentBOMLine;
import com.teamcenter.rac.kernel.TCComponentItem;
import com.teamcenter.rac.kernel.TCComponentItemRevision;

import ru.idealplm.specification.oceanos.handlers.linehandlers.OceanosBlockLineHandler;
import ru.idealplm.specification.blockline.BlockLine;
import ru.idealplm.specification.core.BlockLineFactory;
import ru.idealplm.specification.core.Error;
import ru.idealplm.specification.core.Specification;
import ru.idealplm.specification.core.Specification.BlockContentType;
import ru.idealplm.specification.core.Specification.BlockType;

public class OceanosBlockLineFactory extends BlockLineFactory
{	
	private final String[] blProps = new String[]
	{
			"Oc9_Zone",
			"bl_sequence_no",
			"bl_quantity",
			"Oc9_Note",
			"Oc9_DisChangeFindNo", //� ��������� � ���������� sequence_no ������ ���� ���������� ��������
			"oc9_KITName",
			"bl_item_uom_tag",
			"Oc9_KITs",
			"SE Assembly Reports"
	};

	@Override
	public BlockLine newBlockLine(TCComponentBOMLine bomLine) {
		try{
			TCComponent item = bomLine.getItem();
			TCComponentItemRevision itemIR = bomLine.getItemRevision();
			String uid = itemIR.getUid();
			String[] properties = bomLine.getProperties(blProps);
			
			if(properties[8].equals("0"))
			{
				return null;
			}
			
			OceanosBlockLineHandler blockLineHandler = new OceanosBlockLineHandler();
			BlockLine resultBlockLine = new BlockLine(blockLineHandler);
			resultBlockLine.attributes.setZone(properties[0]);
			resultBlockLine.attributes.setPosition(properties[1]);
			resultBlockLine.isRenumerizable = properties[4].trim().equals("");
			resultBlockLine.uid = uid;
			
			if(item.getType().equals("Oc9_CompanyPart")){
				if(!properties[7].isEmpty()){
					Specification.errorList.addError(new Error("ERROR", "������ � ��������������� " + item.getProperty("item_id") + " ����� ������ �� ��������."));
				}
				String typeOfPart = item.getProperty("oc9_TypeOfPart");
				if(typeOfPart.equals("��������� �������") || typeOfPart.equals("��������")){
					/*********************** ������ � ��������� ***********************/
					AIFComponentContext[] relatedDocs = bomLine.getItemRevision().getRelated("Oc9_DocRel");
					for(AIFComponentContext relatedDoc : relatedDocs){
						String docID = relatedDoc.getComponent().getProperty("item_id");
						if(docID.equals(bomLine.getItem().getProperty("item_id"))){
							String format = ((TCComponentItem)relatedDoc.getComponent()).getLatestItemRevision().getProperty("oc9_Format");
							resultBlockLine.attributes.setFormat(format);
							break;
						}
					}
					resultBlockLine.attributes.setId(item.getProperty("item_id"));
					resultBlockLine.attributes.setName(itemIR.getProperty("object_name"));
					resultBlockLine.attributes.setQuantity(properties[2]);
					resultBlockLine.attributes.setRemark(properties[3]);
					resultBlockLine.addRefBOMLine(bomLine);
					if(typeOfPart.equals("��������� �������")){
						resultBlockLine.blockContentType = BlockContentType.ASSEMBLIES;
						//blockList.getBlock(BlockContentType.ASSEMBLIES, isDefault?"Default":"ME").addBlockLine(uid, resultBlockLine);
					} else {
						resultBlockLine.blockContentType = BlockContentType.COMPLEXES;
						//blockList.getBlock(BlockContentType.COMPLEXES, isDefault?"Default":"ME").addBlockLine(uid, resultBlockLine);
					}
					resultBlockLine.blockType = BlockType.DEFAULT;
				} else if(typeOfPart.equals("������")){
					/*****************************������*********************************/
					boolean hasDraft = false;
					AIFComponentContext[] relatedDocs = bomLine.getItemRevision().getRelated("Oc9_DocRel");
					String itemId = bomLine.getItem().getProperty("item_id");
					if(itemId.contains("-")) itemId = itemId.split("-")[0]; // ���� � id ���� "-", �� ������ ����� ����� id �� ����
					for(AIFComponentContext relatedDoc : relatedDocs){
						String docID = relatedDoc.getComponent().getProperty("item_id");
						if(docID.equals(itemId))
						{
							String format = ((TCComponentItem)relatedDoc.getComponent()).getLatestItemRevision().getProperty("oc9_Format");
							resultBlockLine.attributes.setFormat(format);
							hasDraft = true;
							break;
						}
						/*else if (bomLine.getItem().getProperty("item_id").contains("-")) //TODO �� ����� ����, ��� ��� �� �����, ���������� ������ itemId ��������
						{
							String[] tmp_item_id = bomLine.getItem().getProperty("item_id").split("-");
							String result_item_id = tmp_item_id[0];
								if(docID.equals(result_item_id))
								{
									String format = ((TCComponentItem)relatedDoc.getComponent()).getLatestItemRevision().getProperty("oc9_Format");
									resultBlockLine.attributes.setFormat(format);
									hasDraft = true;
									break;
								}
							break;	
						}*/
					}
					if(!hasDraft){
						if(itemIR.getProperty("oc9_CADMaterial").equals("")){
							Specification.errorList.addError(new Error("ERROR", "� ��-������ � ��������������� " + item.getProperty("item_id") + " �� �������� ������� \"�������� ��������\""));
						}
						resultBlockLine.attributes.setFormat("��");
						resultBlockLine.attributes.setName(itemIR.getProperty("object_name") + "\n" + itemIR.getProperty("oc9_CADMaterial") + " " + itemIR.getProperty("oc9_AddNote"));
					} else {
						resultBlockLine.attributes.setName(itemIR.getProperty("object_name"));
					}
					resultBlockLine.attributes.setId(item.getProperty("item_id"));
					resultBlockLine.attributes.setQuantity(properties[2]);
					if(hasDraft){
						resultBlockLine.attributes.setRemark(properties[3]);
					} else {
						if(!itemIR.getProperty("oc9_mass").trim().equals("")) {
							resultBlockLine.attributes.setRemark(itemIR.getProperty("oc9_mass") + " ��"/* + properties[3]*/);
							resultBlockLine.attributes.getRemark().insert(properties[3]);
						} else {
							resultBlockLine.attributes.setRemark(properties[3]);
						}
					}
					resultBlockLine.addRefBOMLine(bomLine);
					AIFComponentContext[] relatedBlanks = bomLine.getItemRevision().getRelated("Oc9_StockRel");
					if(relatedBlanks.length>0){
						BlockLine blank = new BlockLine(blockLineHandler);
						TCComponentItem blankItem = (TCComponentItem)relatedBlanks[0].getComponent();
						blank.attributes.setPosition("-");
						blank.attributes.setName(blankItem.getLatestItemRevision().getProperty("object_name") + " " + "�������-��������� ��� " + resultBlockLine.attributes.getId());
						if(!blankItem.getType().equals("CommercialPart")){
							relatedDocs = ((TCComponentItem)relatedBlanks[0].getComponent()).getLatestItemRevision().getRelated("Oc9_DocRel");
							for(AIFComponentContext relatedDoc : relatedDocs){
								String docID = relatedDoc.getComponent().getProperty("item_id");
								if(docID.equals(blankItem.getProperty("item_id"))){
									String format = ((TCComponentItem)relatedDoc.getComponent()).getLatestItemRevision().getProperty("oc9_Format");
									blank.attributes.setFormat(format);
									break;
								}
							}
							blank.attributes.setId(relatedBlanks[0].getComponent().getProperty("item_id"));
						}
						resultBlockLine.getAttachedLines().add(blank);
					}
					resultBlockLine.blockContentType = BlockContentType.DETAILS;
					resultBlockLine.blockType = BlockType.DEFAULT;
					//blockList.getBlock(BlockContentType.DETAILS, isDefault?"Default":"ME").addBlockLine(uid, resultBlockLine);
				} else if(typeOfPart.equals("��������")){
					/****************************���������********************************/
					resultBlockLine.attributes.setId(item.getProperty("item_id"));
					resultBlockLine.attributes.setName(itemIR.getProperty("object_name"));
					resultBlockLine.attributes.setQuantity(properties[2]);
					resultBlockLine.addRefBOMLine(bomLine);
					resultBlockLine.blockContentType = BlockContentType.KITS;
					resultBlockLine.blockType = BlockType.DEFAULT;
					//blockList.getBlock(BlockContentType.KITS, isDefault?"Default":"ME").addBlockLine(uid, resultBlockLine);
				} else if(typeOfPart.equals("")){
					Specification.errorList.addError(new Error("ERROR", "� ��������� � ������������ " + properties[2] + "����������� �������� �������� \"��� �������\""));
				}
			} else if(item.getType().equals("CommercialPart")){
				/****************************������������********************************/
				resultBlockLine.attributes.setName(item.getProperty("oc9_RightName"));
				if(item.getProperty("oc9_RightName").equals("������������ �� �����������")){
					resultBlockLine.addProperty("bNameNotApproved", "true");
				}
				resultBlockLine.attributes.setQuantity(properties[2]);
				resultBlockLine.attributes.setRemark(properties[3]);
				resultBlockLine.addRefBOMLine(bomLine);
				if(!properties[7].isEmpty()){
					resultBlockLine.attributes.createKits();
					resultBlockLine.attributes.addKit(properties[7], properties[5], properties[2].isEmpty()?1:Integer.parseInt(properties[2]));
				}
				if(item.getProperty("oc9_TypeOfPart").equals("������ �������")){
					resultBlockLine.blockContentType = BlockContentType.OTHERS;
					resultBlockLine.blockType = BlockType.DEFAULT;
					//blockList.getBlock(BlockContentType.OTHERS, isDefault?"Default":"ME").addBlockLine(uid, resultBlockLine);
				} else {
					resultBlockLine.blockContentType = BlockContentType.STANDARDS;
					resultBlockLine.blockType = BlockType.DEFAULT;
					//blockList.getBlock(BlockContentType.STANDARDS, isDefault?"Default":"ME").addBlockLine(uid, resultBlockLine);
				}
			} else if(item.getType().equals("Oc9_Material")){
				/****************************���������********************************/
				String seCutLength = bomLine.getProperty("SE Cut Length");
				resultBlockLine.attributes.setName(item.getProperty("oc9_RightName") + (seCutLength.isEmpty()?"":(" L="+seCutLength)));
				if(item.getProperty("oc9_RightName").equals("������������ �� �����������")){
					resultBlockLine.addProperty("bNameNotApproved", "true");
				}
				resultBlockLine.attributes.setQuantity(properties[2]);
				resultBlockLine.addRefBOMLine(bomLine);
				resultBlockLine.addProperty("UOM", properties[6]);
				resultBlockLine.addProperty("SE Cut Length", seCutLength);
				resultBlockLine.addProperty("CleanName", item.getProperty("oc9_RightName"));
				resultBlockLine.addProperty("FromGeomMat", "");
				resultBlockLine.addProperty("FromMat", "true");
				if(!seCutLength.isEmpty() && !properties[6].equals("*")){
					Specification.errorList.addError(new Error("ERROR", "� ��������� � ��������������� " + item.getProperty("item_id") + " ������� ��������� ������� �� ��."));
				}
				if(!properties[7].isEmpty()){
					resultBlockLine.attributes.createKits();
					resultBlockLine.attributes.addKit(properties[7], properties[5], properties[2].isEmpty()?1:Integer.parseInt(properties[2]));
				}
				resultBlockLine.blockContentType = BlockContentType.MATERIALS;
				resultBlockLine.blockType = BlockType.DEFAULT;
			} else if(item.getType().equals("Oc9_GeomOfMat")){
				/*************************��������� ����������****************************/
				AIFComponentContext[] materialBOMLines = bomLine.getChildren();
				if(materialBOMLines.length>0){
					if(materialBOMLines.length>1){
						Specification.errorList.addError(new Error("ERROR", "� ������� ��������� ��������� � ��������������� " + item.getProperty("item_id") + " ������������ ����� ������ ���������."));
					}
					TCComponentItemRevision materialIR = ((TCComponentBOMLine) materialBOMLines[0].getComponent()).getItemRevision();
					TCComponentItem materialI = ((TCComponentBOMLine) materialBOMLines[0].getComponent()).getItem();
					if(materialI.getProperty("oc9_RightName").equals("������������ �� �����������")){
						resultBlockLine.addProperty("bNameNotApproved", "true");
					}
					String quantityMS = ((TCComponentBOMLine) materialBOMLines[0].getComponent()).getProperty("bl_quantity");
					float quantityMD = Float.parseFloat(quantityMS.equals("")?"1":quantityMS);
					int quantotyGD = Integer.parseInt(properties[2].equals("")?"1":properties[2]);
					String uom = ((TCComponentBOMLine) materialBOMLines[0].getComponent()).getProperty("bl_item_uom_tag");
					uom = uom.equals("*")?"":uom;
					resultBlockLine.attributes.setName(materialIR.getItem().getProperty("oc9_RightName"));
					resultBlockLine.attributes.setQuantity(String.valueOf(quantityMD*quantotyGD));
					resultBlockLine.attributes.setRemark(uom);
					resultBlockLine.addRefBOMLine(bomLine);
					resultBlockLine.addProperty("SE Cut Length", "");
					resultBlockLine.addProperty("CleanName", materialIR.getItem().getProperty("oc9_RightName"));
					resultBlockLine.addProperty("FromGeomMat", "true");
					resultBlockLine.addProperty("FromMat", "");
					resultBlockLine.uid = materialIR.getUid();
					resultBlockLine.blockContentType = BlockContentType.MATERIALS;
					resultBlockLine.blockType = BlockType.DEFAULT;
				} else {
					Specification.errorList.addError(new Error("ERROR", "� ������� ��������� ��������� � ��������������� " + item.getProperty("item_id") + " ����������� ��������."));
				}
			}
			return resultBlockLine;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

}
