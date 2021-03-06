package com.borqs.se.home3d;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;

import com.borqs.se.engine.SEScene;
import com.borqs.se.engine.SECommand;
import com.borqs.se.engine.SELoadResThread;
import com.borqs.se.engine.SEObject;
import com.borqs.se.engine.SEVector.SEVector3f;
import com.borqs.se.home3d.ProviderUtils.ModelColumns;
import com.borqs.se.widget3d.NormalObject;
import com.borqs.se.widget3d.ObjectInfo;

public class ModelObjectsManager {

    public HashMap<String, ModelInfo> mModels;
    private HashMap<String, ModelInfo> mMissingModels;

    private HashMap<String, ModelInfo> getModelMap() {
        if (null == mModels) {
            mModels = new HashMap<String, ModelInfo>();
        }
        return mModels;
    }
    private HashMap<String, ModelInfo> getMissingModelMap() {
        if (null == mMissingModels) {
            mMissingModels = new HashMap<String, ModelInfo>();
        }
        return mMissingModels;
    }


    public ModelObjectsManager() {
        init();
    }

    private void init() {
        Context context = HomeManager.getInstance().getContext();       
        ContentResolver resolver = context.getContentResolver();
        Cursor modelCursor = resolver.query(ModelColumns.CONTENT_URI, null, null, null, null);
        while (modelCursor.moveToNext()) {
            ModelInfo modelInfo = ModelInfo.CreateFromDB(modelCursor);
            if (HomeUtils.DEBUG || !modelInfo.mAssetsPath.contains(HomeUtils.SDCARD_PATH)) {
                // #########判断模型文件是否存在，假如不存在不加入列表中###########
                boolean modelExist = true;
                if (modelInfo.mAssetsPath.startsWith("assets/")) {
                    try {
                        InputStream is = context.getAssets().open(modelInfo.mAssetsPath.substring(7) + "/models_config.xml");
                        if (is== null) {
                            modelExist = false;
                        } else {
                            is.close();
                        }
                    } catch (IOException e) {
                        modelExist = false;
                    }

                } else {
                    File modelFile = new File(modelInfo.mAssetsPath);
                    if (!modelFile.exists()) {
                        modelExist = false;
                    }
                }
                if (modelExist) {
                    getModelMap().put(modelInfo.mName, modelInfo);
                } else {
                    getMissingModelMap().put(modelInfo.mName, modelInfo);
                }
            }
        }
        modelCursor.close();
    }
    /**
     * When the 3Dscene started, will load the preload models which for
     * can be added into the scene quickly.
     * @param finish the finish behavior after preloaded.
     */
    public void loadPreLoadModel(final SEScene scene, final Runnable finish) {
        Iterator<Entry<String, ModelInfo>> iter = mModels.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, ModelInfo> entry = iter.next();
            final ModelInfo modelInfo = entry.getValue();
            if (modelInfo.mType.equals("IconBackground")) {
                if (!modelInfo.hasInstance()) {
                    final SEObject baseModel = new SEObject(scene, modelInfo.mName, 0);
                    modelInfo.load3DMAXModel(scene, scene.getContentObject(), baseModel, false, new Runnable() {
                        public void run() {
                            baseModel.setVisible(false);
                            scene.getContentObject().addChild(baseModel, false);
                            register(baseModel);
                            // adjust the size of 3DMax model begin
                            resizeModel(scene, modelInfo);
                            // adjust the size of 3DMax model end
                        }
                    });
                }
            }
        }
        SELoadResThread.getInstance().process(new Runnable() {
            public void run() {
                new SECommand(scene) {
                    public void run() {
                        if (finish != null) {
                            finish.run();
                        }
                    }
                }.execute();
            }
        });
    }

    /**
     * When the 3Dscene started,After load all objects finished, will load the preload models which for
     * can be added into the scene quickly.
     * @param finish the finish behavior after afterloaded.
     */
    public void loadAfterLoadModel(final SEScene scene, final Runnable finish) {
        Iterator<Entry<String, ModelInfo>> iter = mModels.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, ModelInfo> entry = iter.next();
            final ModelInfo modelInfo = entry.getValue();
            if (modelInfo.mType.equals("Folder") || modelInfo.mType.equals("Recycle")
                    || modelInfo.mType.equals("IconBox") || modelInfo.mType.equals("shop")
                    || modelInfo.mType.equals("showbox")) {
                if (!modelInfo.hasInstance()) {
                    final SEObject baseModel = new SEObject(scene, modelInfo.mName, 0);
                    modelInfo.load3DMAXModel(scene, scene.getContentObject(), baseModel, false, new Runnable() {
                        public void run() {                           
                            baseModel.setVisible(false);
                            scene.getContentObject().addChild(baseModel, false);
                            register(baseModel);
                            // adjust the size of 3DMax model begin
                            resizeModel(scene, modelInfo);
                            // adjust the size of 3DMax model end
                        }

                    });
                }
            }
        }

        SELoadResThread.getInstance().process(new Runnable() {
            public void run() {
                new SECommand(scene) {
                    public void run() {
                        if (finish != null) {
                            finish.run();
                        }
                    }
                }.execute();
            }
        });
    }
    
    public void clearModelStatus() {
        Iterator<Entry<String, ModelInfo>> iter = mModels.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, ModelInfo> entry = iter.next();
            ModelInfo modelInfo = entry.getValue();
            modelInfo.getInstances().clear();
        }
    }

    /**
     * 克隆物体，并且对物体大小进行适配
     */
    public void createQuickly(SEObject parent, SEObject modelObject) {
        ModelInfo modelInfo = mModels.get(modelObject.mName);
        if (modelInfo.hasInstance()) {
            // clone from base model
            SEObject baseModel = modelInfo.getInstances().get(0);
            baseModel.cloneObject_JNI(parent, modelObject.mIndex);
            modelObject.setHasBeenAddedToEngine(true);
            parent.addChild(modelObject, false);
            register(modelObject);
            adjustTheSizeOfObject(modelInfo, modelObject);
        }
    }

    /**
     * 调整添加入房间物体的大小，以及物体占用的格子数
     * 注意在墙上的物体其Z坐标上的点最大不能超过0，否则可能会被嵌入墙面
     */
    private void adjustTheSizeOfObject(ModelInfo modelInfo, SEObject modelObject) {
        if (modelObject instanceof NormalObject) {
            NormalObject normalObject = (NormalObject) modelObject;
            if (modelInfo.mSlotType == ObjectInfo.SLOT_TYPE_DESKTOP) {
                // 对应桌面的物体只调整中心点的位置，使得中心点的位置为物体的底部

            } else if (modelInfo.mSlotType == ObjectInfo.SLOT_TYPE_WALL) {
                HomeScene homeScene = HomeManager.getInstance().getHomeScene();
                ObjectInfo objectInfo = normalObject.getObjectInfo();
                SEObject modelComponenet = normalObject.getModelComponenet();
                float maxWidth = getWidthOfWidget(homeScene, Float.MAX_VALUE);
                float maxHight = getHeightOfWidget(homeScene, Float.MAX_VALUE);
                float scale;
                SEVector3f translate;
                float sizeX = 2 * Math.max(modelInfo.mMaxPoint.getX(), -modelInfo.mMinPoint.getX());
                float sizeZ = 2 * Math.max(modelInfo.mMaxPoint.getZ(), -modelInfo.mMinPoint.getZ());
                // 假如在配置文件中没有指定物体的占用的格子数，那么我们根据物体的最小点和最大点来计算物体应该占用几个格子
                if (objectInfo.getSpanX() <= 0 || objectInfo.getSpanY() <= 0) {
                    if (sizeX > maxWidth || sizeZ > maxHight) {
                        scale = Math.min(maxWidth / sizeX, maxHight / sizeZ);
                        translate = new SEVector3f(0, -objectInfo.mMaxPoint.getY() * scale, 0);
                        objectInfo.setSpanX(homeScene.getHomeSceneInfo().mCellCountX);
                        objectInfo.setSpanX(homeScene.getHomeSceneInfo().mCellCountY);
                    } else {
                        int spanX = 0;
                        int spanY = 0;
                        while (sizeX <= getWidthOfWidget(homeScene, spanX)) {
                            spanX++;
                        }
                        while (sizeZ <= getHeightOfWidget(homeScene, spanY)) {
                            spanY++;
                        }
                        spanX--;
                        spanY--;
                        maxWidth = getWidthOfWidget(homeScene, spanX);
                        maxHight = getHeightOfWidget(homeScene, spanY);
                        scale = Math.min(maxWidth / sizeX, maxHight / sizeZ);
                        translate = new SEVector3f(0, -objectInfo.mMaxPoint.getY() * scale, 0);
                        objectInfo.setSpanX(spanX);
                        objectInfo.setSpanX(spanY);
                    }
                    // 假如在配置文件中指定了物体应该占用几个格子，那么我们根据格子的大小对物体进行缩放
                } else {
                    int oldSpanX = objectInfo.getSpanX();
                    if (oldSpanX > homeScene.getHomeSceneInfo().mCellCountX) {
                        objectInfo.setSpanX(homeScene.getHomeSceneInfo().mCellCountX);
                        objectInfo
                                .setSpanY(objectInfo.getSpanY() - oldSpanX + homeScene.getHomeSceneInfo().mCellCountX);
                    }

                    int oldSpanY = objectInfo.getSpanY();
                    if (oldSpanY > homeScene.getHomeSceneInfo().mCellCountY) {
                        objectInfo.setSpanY(homeScene.getHomeSceneInfo().mCellCountY);
                        objectInfo
                                .setSpanX(objectInfo.getSpanX() - oldSpanY + homeScene.getHomeSceneInfo().mCellCountY);
                    }
                    maxWidth = getWidthOfWidget(homeScene, objectInfo.getSpanX());
                    maxHight = getHeightOfWidget(homeScene, objectInfo.getSpanY());
                    scale = Math.min(maxWidth / sizeX, maxHight / sizeZ);
                    translate = new SEVector3f(0, -objectInfo.mMaxPoint.getY() * scale, 0);
                }
                objectInfo.mMinPoint = modelInfo.mMinPoint.mul(scale);
                objectInfo.mMaxPoint = modelInfo.mMaxPoint.mul(scale);
                objectInfo.mMinPoint.selfAdd(translate);
                objectInfo.mMaxPoint.selfAdd(translate);
                modelComponenet.setUserTranslate(translate);
                modelComponenet.setUserScale(new SEVector3f(scale, scale, scale));
            }
        }
    }

    private float getWidthOfWidget(HomeScene homeScene, float spanX) {
        float gridSizeX = homeScene.getHomeSceneInfo().mCellWidth + homeScene.getHomeSceneInfo().mWidthGap;
        if (spanX > homeScene.getHomeSceneInfo().mCellCountX) {
            return homeScene.getHomeSceneInfo().mCellCountX * gridSizeX - homeScene.getHomeSceneInfo().mWidthGap;
        } else {
            return spanX * gridSizeX - homeScene.getHomeSceneInfo().mWidthGap;
        }
    }

    private float getHeightOfWidget(HomeScene homeScene, float spanY) {
        float gridSizeY = homeScene.getHomeSceneInfo().mCellHeight + homeScene.getHomeSceneInfo().mHeightGap;
        if (spanY > homeScene.getHomeSceneInfo().mCellCountY) {
            return homeScene.getHomeSceneInfo().mCellCountY * gridSizeY - homeScene.getHomeSceneInfo().mHeightGap;
        } else {
            return spanY * gridSizeY - homeScene.getHomeSceneInfo().mHeightGap;
        }
    }

    /**
     * If modelObject's mIndex does not equals 0, we will save a base object
     * which index is 0. All objects are cloned from the base object
     */
    public void create(final SEObject parent, final SEObject modelObject, final Runnable finish) {
        final ModelInfo modelInfo = mModels.get(modelObject.mName);
        if (modelObject.mIndex == 0) {
            throw new IllegalArgumentException(
                    "If the object is created from 3DMax model, its index should not equals 0");
        }
        if (modelInfo.hasInstance()) {
            //假如模型在场景中已经有实例了，直接克隆出一个
            createQuickly(parent, modelObject);
            if (finish != null) {
                finish.run();
            }
        } else if (modelInfo.isOnLoading()) {
            //假如模型正在加载，那么等加载完了后克隆出一个
            SELoadResThread.getInstance().process(new Runnable() {
                public void run() {
                    new SECommand(parent.getScene()) {
                        public void run() {
                            createQuickly(parent, modelObject);
                            if (finish != null) {
                                finish.run();
                            }
                        }
                    }.execute();
                }
            });
        } else {
            //假如模型还没有加载，那么开始加载，等加载完了后克隆出一个
            final SEObject baseModel = new SEObject(parent.getScene(), modelInfo.mName, 0);
            createBaseObject(parent.getScene().getContentObject(), baseModel, new Runnable() {
                public void run() {
                    baseModel.setVisible(false);
                    createQuickly(parent, modelObject);
                    if (finish != null) {
                        finish.run();
                    }
                }
            });
        }
    }
    /**
     * 模型文件结构如桌子（相机和电话是在桌子加载完成后加入桌子的节点）：
     *              桌子
     *          _____|_____
     *      ___|___        |
     *     |       |    桌子_model
     *    相机 ... 电话   ___|___
     *                  |      |
     *                桌子腿...桌面
     *                
     * 根据配置文件中localTrans来调整模型的大小，如桌子：
     * 我们只需要调整“桌子_model”物体的大小而不用调整相机和电话的大小
     */
    private void resizeModel(SEScene scene, ModelInfo modelInfo) {

        SEObject modelOf3DMax = new SEObject(scene, modelInfo.mName + "_model");
        if (modelInfo.mCompensationTrans != null) {
            modelOf3DMax.setHasBeenAddedToEngine(true);
            modelOf3DMax.setLocalTranslate(modelInfo.mCompensationTrans.mTranslate);
            modelOf3DMax.setLocalRotate(modelInfo.mCompensationTrans.mRotate);
            modelOf3DMax.setLocalScale(modelInfo.mCompensationTrans.mScale);
        }
    }

    public ModelInfo findModelInfo(String name) {
        return mModels.get(name);
    }

    public ArrayList<ModelInfo> findModelInfoByType(String type) {
        ArrayList<ModelInfo> flyers = new ArrayList<ModelInfo>();
        for (Map.Entry<String, ModelInfo> entry : mModels.entrySet()) {
            ModelInfo flyer = entry.getValue();
            if (!flyer.mType.equals(type)) {
                continue;
            }
            if (flyer.mSupportScreen < 0 || flyer.mSupportScreen == HomeManager.getInstance().getRequestedOrientation()) {
                flyers.add(flyer);
            }
        }
        return flyers;
    }

    public ArrayList<ModelInfo> getModelsCanBeShowedInMenu() {
        ArrayList<ModelInfo> flyers = new ArrayList<ModelInfo>();
        for (Map.Entry<String, ModelInfo> entry : mModels.entrySet()) {
            ModelInfo flyer = entry.getValue();
            if (flyer.mType.equals("Airship") || flyer.mType.equals("Folder") || flyer.mType.equals("Recycle")
                    || flyer.mType.equals("IconBackground") || flyer.mType.equals("showbox")
                    || flyer.mType.equals("House") || flyer.mType.equals("Sky") || flyer.mType.equals("Desk")) {
                continue;
            }
            if (ObjectInfo.isBottomDecoratorType(flyer.mType)) {
                continue;
            }
            if (flyer.mSupportScreen < 0 || flyer.mSupportScreen == HomeManager.getInstance().getRequestedOrientation()) {
                flyers.add(flyer);
            }

        }
        return flyers;
    }

    /**
     * Objects menu can call this method, or we dose not need clone this object
     */
    public void createBaseObject(final SEObject parent, final SEObject modelObject, final Runnable finish) {
        final ModelInfo modelInfo = mModels.get(modelObject.mName);
        if (modelObject.mIndex == 0) {
            // for single instance object
            modelInfo.load3DMAXModel(parent.getScene(), parent, modelObject, false, new Runnable() {
                public void run() {
                    parent.addChild(modelObject, false);
                    resizeModel(parent.getScene(), modelInfo);
                    register(modelObject);
                    if (finish != null) {
                        finish.run();
                    }
                }

            });
        } else {
            throw new IllegalArgumentException("The index of base object should equals 0");
        }
    }

    /**
     * Only object menu can call this method, release the base object if we do
     * not need it
     */
    public boolean releaseBaseModel(ModelInfo modelInfo) {
        if (modelInfo.getInstances().size() == 1) {
            if (!modelInfo.mType.equals("shop") && !modelInfo.mType.equals("IconBox")) {
                modelInfo.stopLoading();
                SEObject model = modelInfo.getInstances().get(0);
                model.getParent().removeChild(model, true);
                modelInfo.getInstances().clear();
                return true;
            }
        } else if (modelInfo.getInstances().size() == 0) {
            if (!modelInfo.mType.equals("shop") && !modelInfo.mType.equals("IconBox")) {
                modelInfo.stopLoading();
                return true;
            }
        }
        return false;
    }

    public void register(SEObject instance) {
        ModelInfo modelInfo = mModels.get(instance.mName);
        if (modelInfo == null) {
            return;
        }
        if (!modelInfo.getInstances().contains(instance)) {
            modelInfo.getInstances().add(instance);
        }
    }

    public void unRegister(SEObject instance) {
        ModelInfo modelInfo = mModels.get(instance.mName);
        if (modelInfo == null) {
            return;
        }
        if (modelInfo.getInstances().contains(instance)) {
            modelInfo.getInstances().remove(instance);
            if (modelInfo.getInstances().size() == 1) {
                releaseBaseObjectWhileNoObjectCloneFromIt(modelInfo);
            }
        }
    }

    /**
     * We will release the last object if we do not need it no longer
     */
    private void releaseBaseObjectWhileNoObjectCloneFromIt(ModelInfo modelInfo) {
        if (!modelInfo.mType.equals("Folder") && !modelInfo.mType.equals("Recycle")
                && !modelInfo.mType.equals("IconBox") && !modelInfo.mType.equals("IconBackground")
                && !modelInfo.mType.equals("shop") && !modelInfo.mType.equals("House")
                && !modelInfo.mType.equals("Sky") && !modelInfo.mType.equals("showbox")) {
            SEObject theLastInstance = modelInfo.getInstances().get(0);
            theLastInstance.getParent().removeChild(theLastInstance, true);
            modelInfo.getInstances().clear();
        }
    }

    /**
     * 我们CBF里面包含的模型结构是： desk子节点为{desk_model}， desk_model子节点为{desktop, desk_legs,
     * desk_circle}。加载模型时可以选择是否需要最上层节点也就是desk节点，当模型需要更新时我们希望保留桌面上的东西，这时可以只替换desk_model
     */
    public void loadModelOnly(final ModelInfo modelInfo, final SEObject parent, final SEObject modelObject,
            final Runnable finish) {
        modelInfo.load3DMAXModel(parent.getScene(), parent, modelObject, true, new Runnable() {
            public void run() {
                resizeModel(parent.getScene(), modelInfo);
                if (finish != null) {
                    finish.run();
                }
            }

        });
    }

}
