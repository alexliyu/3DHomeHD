package com.borqs.se.widget3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;
import android.widget.Toast;

import com.borqs.se.R;
import com.borqs.se.engine.*;
import com.borqs.se.engine.SEScene.SCENE_CHANGED_TYPE;
import com.borqs.se.engine.SEVector.AXIS;
import com.borqs.se.engine.SEVector.SEVector2i;
import com.borqs.se.engine.SEVector.SEVector3f;
import com.borqs.se.home3d.ApplicationMenu;
import com.borqs.se.home3d.HomeScene;
import com.borqs.se.home3d.HomeSceneInfo;
import com.borqs.se.home3d.HomeUtils;
import com.borqs.se.home3d.HomeManager;
import com.borqs.se.home3d.ModelInfo;
import com.borqs.se.widget3d.ObjectInfo.ObjectSlot;

public class NormalObject extends SEObject {
    private SETransParas mStartTranspara;
    private ObjectInfo mObjectInfo;
    private boolean mOnFinger;
    private boolean mIsFresh;
    private boolean mHasInit;
    private NormalObject mChangedToObj;
    private VesselObject mRoot;
    private SEVector2i mAdjustTouch;
    private ObjectSlot mPreObjectSlot;

    // for long click object
    private VesselLayer mPreLayer;
    private HomeScene mHomeScene;
    public boolean mSlotChanged;
    public SEObject mModelObject;

    /**
     * 物体的组件： 需和孩子区别开，如桌子由桌面和桌腿组成: 
     * 桌子上的物体作为孩子，桌子本身作为组件
     * 注意：组件默认是不可以接收点击事件，如需要接受调用： setClickable(true); setIsEntirety(true);
     */
    private List<SEObject> mComponenetObjects = new ArrayList<SEObject>();
    private List<SEObject> mChildObjects = new ArrayList<SEObject>();
    private SEObject mModelComponenet;

    public NormalObject(HomeScene homeScene, String name, int index) {
        super(homeScene, name, index);
        mHomeScene = homeScene;
        setIsGroup(true);
        mOnFinger = false;
        mIsFresh = false;
        mHasInit = false;
        setLabelVisible();
    }

    public HomeScene getHomeScene() {
        return mHomeScene;
    }

    public HomeSceneInfo getHomeSceneInfo() {
        return mHomeScene.getHomeSceneInfo();
    }

    protected SEVector.SEVector2f getWallCellSize() {
        HomeSceneInfo info = getHomeSceneInfo();
        return new SEVector.SEVector2f(info.mCellWidth + info.mWidthGap, info.mCellHeight + info.mHeightGap);
    }

    @Override
    public void addChild(SEObject obj, boolean create) {
        super.addChild(obj, create);
        if (!mChildObjects.contains(obj)) {
            mChildObjects.add(obj);
        }
    }

    @Override
    public List<SEObject> getChildObjects() {
        return mChildObjects;
    }

    public List<SEObject> getComponenetObjects() {
        return mComponenetObjects;
    }

    /**
     * @param obj
     *            组件
     * @param create
     *            是否创建，假如组件已经存在了就不需要创建, 如：桌面在桌子模型加载完后就已经存在了，这个时候不需要创建添加进来即可
     */
    public void addComponenetObject(SEObject obj, boolean create) {
        super.addChild(obj, create);
        obj.setClickable(false);
        obj.setIsEntirety(false);
        if (!mComponenetObjects.contains(obj)) {
            mComponenetObjects.add(obj);
        }
    }

    @Override
    public void removeChild(SEObject child, boolean release) {
        super.removeChild(child, release);
        if (mChildObjects.contains(child)) {
            mChildObjects.remove(child);
        }
    }

    public void changeModelInfo(ModelInfo modelInfo) {
        for (SEObject componenetObject : mComponenetObjects) {
            super.removeChild(componenetObject, false);
        }
        mComponenetObjects.clear();
        mModelComponenet = null;
        getObjectInfo().setModelInfo(modelInfo);
        if (getObjectInfo().mIsNativeObject) {
            // 添加模型组件。模型文件结构如桌子：
            //              桌子
            //          _____|_____
            //      ___|___        |
            //     |       |    桌子_model
            //    相机 ... 电话   ___|___
            //                  |      |
            //                桌子腿...桌面
            SEObject modelObject = new SEObject(getScene(), mName + "_model", mIndex);
            modelObject.setHasBeenAddedToEngine(true);
            addComponenetObject(modelObject, false);
            if (getObjectInfo().mModelInfo.mComponentAttributes != null) {
                for (SEComponentAttribute componentAttribute : getObjectInfo().mModelInfo.mComponentAttributes) {
                    SEObject componentObject = new SEObject(getScene(), componentAttribute.mComponentName, mIndex);
                    componentObject.setHasBeenAddedToEngine(true);
                    // 在模型加载完后，组件就已经存在了，所以create=false
                    addComponenetObject(componentObject, false);
                }
            }
        }
    }

    /**
     * @param regularName
     *            组件的规范名
     * @return 返回组件的SEObject对象，通过该对象可以操作组件
     */
    public SEObject findComponenetObjectByRegularName(String regularName) {
        if (getObjectInfo().mIsNativeObject) {
            SEComponentAttribute componentAttribute = getObjectInfo().mModelInfo
                    .findComponentAttrByRegularName(regularName);
            if (componentAttribute != null) {
                for (SEObject obj : mComponenetObjects) {
                    if (obj.getName().equals(componentAttribute.mComponentName)) {
                        return obj;
                    }
                }
            }
        }
        return null;
    }

    /**
     * @param regularName
     *            组件的规范名
     * @return 返回组件的SEObject对象，通过该对象可以操作组件
     */
    public List<SEObject> findComponenetObjectsByRegularName(String regularName) {
        List<SEObject> componenetObjects = null;
        if (getObjectInfo().mIsNativeObject) {
            List<SEComponentAttribute> componentAttributes = getObjectInfo().mModelInfo
                    .findComponentAttrsByRegularName(regularName);
            if (componentAttributes != null) {
                for (SEComponentAttribute componentAttribute : componentAttributes) {
                    for (SEObject obj : mComponenetObjects) {
                        if (obj.getName().equals(componentAttribute.mComponentName)) {
                            if (componenetObjects == null) {
                                componenetObjects = new ArrayList<SEObject>();
                            }
                            componenetObjects.add(obj);
                        }
                    }
                }
            }
        }
        return componenetObjects;
    }

    /**
     * 添加模型组件。模型文件结构如桌子：
     *              桌子
     *          _____|_____
     *      ___|___        |
     *     |       |    桌子_model
     *    相机 ... 电话   ___|___
     *                  |      |
     *                桌子腿...桌面
     *                
     * @return 返回：桌子_model
     */
    public SEObject getModelComponenet() {
        if (mModelComponenet == null) {
            for (SEObject obj : mComponenetObjects) {
                if (obj.getName().equals(getName() + "_model")) {
                    mModelComponenet = obj;
                    break;
                }
            }
        }
        return mModelComponenet;
    }

    /**
     * @param componenetObject
     *            组件
     * @param release
     *            是否释放组件，释放后组件永远消失
     */
    public void removeComponenetObject(SEObject componenetObject, boolean release) {
        super.removeChild(componenetObject, release);
        if (mComponenetObjects.contains(componenetObject)) {
            mComponenetObjects.remove(componenetObject);
        }
    }

    /**
     * when long click object, popup a dialog which has the change bind app
     * menu.
     * 
     * @param canBeResized
     */
    public void setCanBeResized(boolean canBeResized) {
        if (canBeResized) {
            getObjectInfo().mActionStatus |= ObjectInfo.ACTION_CAN_RESIZE;
        } else {
            getObjectInfo().mActionStatus &= ~ObjectInfo.ACTION_CAN_RESIZE;
        }
    }

    /**
     * when long click object, popup a dialog which has the uninstall app menu.
     * 
     * @param canUninstall
     */
    public void setCanUninstall(boolean canUninstall) {
        if (canUninstall) {
            getObjectInfo().mActionStatus |= ObjectInfo.ACTION_CAN_UNINSATLL;
        } else {
            getObjectInfo().mActionStatus &= ~ObjectInfo.ACTION_CAN_UNINSATLL;
        }
    }

    /**
     * when long click object, popup a dialog which has the change bind app
     * menu.
     * 
     * @param canChangeBind
     */
    public void setCanChangeBind(boolean canChangeBind) {
        if (canChangeBind) {
            getObjectInfo().mActionStatus |= ObjectInfo.ACTION_CAN_CHANGE_BIND;
        } else {
            getObjectInfo().mActionStatus &= ~ObjectInfo.ACTION_CAN_CHANGE_BIND;
        }
    }

    public void setCanChangeIcon(boolean canChangeIcon) {
        if (canChangeIcon) {
            getObjectInfo().mActionStatus |= ObjectInfo.ACTION_CAN_CHANGE_ICON;
        } else {
            getObjectInfo().mActionStatus &= ~ObjectInfo.ACTION_CAN_CHANGE_ICON;
        }
    }

    public void setCanChangeLabel(boolean canChangeLabel) {
        if (canChangeLabel) {
            getObjectInfo().mActionStatus |= ObjectInfo.ACTION_CAN_CHANGE_LABEL;
        } else {
            getObjectInfo().mActionStatus &= ~ObjectInfo.ACTION_CAN_CHANGE_LABEL;
        }
    }

    public void setCanChangeSetting(boolean canChangeSetting) {
        if (canChangeSetting) {
            getObjectInfo().mActionStatus |= ObjectInfo.ACTION_SHOW_SETTING;
        } else {
            getObjectInfo().mActionStatus &= ~ObjectInfo.ACTION_SHOW_SETTING;
        }
    }

    public void setCanChangeColor(boolean canChangeColor) {
        if (canChangeColor) {
            getObjectInfo().mActionStatus |= ObjectInfo.ACTION_CAN_CHANGE_COLOR;
        } else {
            getObjectInfo().mActionStatus &= ~ObjectInfo.ACTION_CAN_CHANGE_COLOR;
        }
    }

    public void setCanChangeImage(boolean canChangeImage) {
        if (canChangeImage) {
            getObjectInfo().mActionStatus |= ObjectInfo.ACTION_CAN_CHANGE_IMAGE;
        } else {
            getObjectInfo().mActionStatus &= ~ObjectInfo.ACTION_CAN_CHANGE_IMAGE;
        }
    }

    public void setCanBeReplaced(boolean replace) {
        if (replace) {
            getObjectInfo().mActionStatus |= ObjectInfo.ACTION_CAN_REPLACE;
        } else {
            getObjectInfo().mActionStatus &= ~ObjectInfo.ACTION_CAN_REPLACE;
        }
    }

    public boolean canBeResized() {
        return (getObjectInfo().mActionStatus & ObjectInfo.ACTION_CAN_RESIZE) != 0;
    }

    public boolean canUninstall() {
        return (getObjectInfo().mActionStatus & ObjectInfo.ACTION_CAN_UNINSATLL) != 0;
    }

    public boolean canChangeBind() {
        return (getObjectInfo().mActionStatus & ObjectInfo.ACTION_CAN_CHANGE_BIND) != 0;
    }

    public boolean canChangeIcon() {
        return (getObjectInfo().mActionStatus & ObjectInfo.ACTION_CAN_CHANGE_ICON) != 0;
    }

    public boolean canChangeLabel() {
        return (getObjectInfo().mActionStatus & ObjectInfo.ACTION_CAN_CHANGE_LABEL) != 0;
    }

    public boolean canChangeSetting() {
        return (getObjectInfo().mActionStatus & ObjectInfo.ACTION_SHOW_SETTING) != 0;
    }

    public boolean canChangeColor() {
        return (getObjectInfo().mActionStatus & ObjectInfo.ACTION_CAN_CHANGE_COLOR) != 0;
    }

    public boolean canChangeImage() {
        return (getObjectInfo().mActionStatus & ObjectInfo.ACTION_CAN_CHANGE_IMAGE) != 0;
    }

    public boolean canBeReplaced() {
        return (getObjectInfo().mActionStatus & ObjectInfo.ACTION_CAN_REPLACE) != 0;
    }

    public void changeColor(int color, boolean post, boolean saveToDB) {
        if (color == -1) {
            return;
        }
        getObjectInfo().mFaceColor = color;
        final float r = Color.red(color) / 255f;
        final float g = Color.green(color) / 255f;
        final float b = Color.blue(color) / 255f;
        if (post) {
            new SECommand(getScene()) {
                public void run() {
                    if (getObjectInfo().mIsNativeObject && getModelComponenet() != null) {
                        getModelComponenet().setUseUserColor(r, g, b);
                    } else {
                        setUseUserColor(r, g, b);
                    }
                }
            }.execute();
        } else {
            if (getObjectInfo().mIsNativeObject && getModelComponenet() != null) {
                getModelComponenet().setUseUserColor(r, g, b);
            } else {
                setUseUserColor(r, g, b);
            }
        } 
        if (saveToDB) {
            getObjectInfo().updateFaceColor(color);
        }
    }

    public void showBackground() {
        for (SEObject object : mChildObjects) {
            if (object instanceof NormalObject) {
                ((NormalObject) object).showBackground();
            }
        }
    }

    public void hideBackground() {
        for (SEObject object : mChildObjects) {
            if (object instanceof NormalObject) {
                ((NormalObject) object).hideBackground();
            }
        }
    }

    public boolean update(SEScene scene) {
        return true;
    }

    public void setIsFresh(boolean fresh) {
        mIsFresh = fresh;
    }

    public boolean isFresh() {
        return mIsFresh;
    }

    public void setChangedToObj(NormalObject origObj) {
        mChangedToObj = origObj;
    }

    public NormalObject getChangedToObj() {
        return mChangedToObj;
    }

    public void setHasInit(boolean hasInit) {
        mHasInit = hasInit;
    }

    public boolean hasInit() {
        return mHasInit;
    }

    public boolean loadAll(final SEObject parent, final Runnable finish) {
        return loadMyself(parent, finish);
    }

    public boolean loadMyself(final SEObject parent, final Runnable finish) {
        if (parent == null) {
            initStatus();
            if (finish != null) {
                finish.run();
            }
            return true;
        }
        if (mObjectInfo.mIsNativeObject) {
            if (mIndex == 0) {
                HomeManager.getInstance().getModelManager().createBaseObject(parent, this, new Runnable() {

                    @Override
                    public void run() {
                        initStatus();
                        if (finish != null) {
                            finish.run();
                        }
                    }
                });

            } else {
                HomeManager.getInstance().getModelManager().create(parent, this, new Runnable() {

                    @Override
                    public void run() {
                        initStatus();
                        if (finish != null) {
                            finish.run();
                        }
                    }
                });
            }
        } else {
            render();
            initStatus();
            if (finish != null) {
                finish.run();
            }
        }
        return true;
    }

    @Override
    public void onRelease() {
        super.onRelease();
        if (mObjectInfo.mIsNativeObject) {
            HomeManager.getInstance().getModelManager().unRegister(this);
        }
        mObjectInfo.releaseDB();

    }

    public void initStatus() {
        setIsEntirety(true);

        if (mObjectInfo.mSlotType == ObjectInfo.SLOT_TYPE_DESKTOP) {
            setBlendSortAxis(AXIS.Z);
        } else {
            setBlendSortAxis(AXIS.Y);
        }
        changeTransParasInParent();

        setOnLongClickListener(new SEObject.OnTouchListener() {
            public void run(SEObject obj) {
                onLongClick();
            }
        });
        setOnClickListener(new SEObject.OnTouchListener() {
            public void run(SEObject obj) {
                handOnClick();
            }
        });
        if (getObjectInfo().mSlotType == ObjectInfo.SLOT_TYPE_DESKTOP) {
            setIsMiniBox(true);
            setNeedGenerateShadowAtGround(true);
        } else if (getObjectInfo().mSlotType == ObjectInfo.SLOT_TYPE_WALL) {
            setIsMiniBox(false);
            setNeedGenerateShadowAtWall(true);
        } else {
            setIsMiniBox(false);
            setNeedGenerateShadowAtGround(false);
            setNeedGenerateShadowAtWall(false);
        }
        if (canChangeColor()) {
            changeColor(getObjectInfo().mFaceColor, false, false);
        }
    }

    @Override
    public void onSceneChanged(SCENE_CHANGED_TYPE changeType) {
        super.onSceneChanged(changeType);
        if (changeType == SCENE_CHANGED_TYPE.NEW_CONFIG) {
            changeTransParasInParent();
        }
    }

    /**
     * when the obect change to another, need copy the status.
     * 
     * @param changedObject
     */
    public void copeStatusTo(NormalObject changedObject) {
        if (getAdjustTouch() == null) {
            changedObject.setAdjustTouch(new SEVector2i());
        } else {
            changedObject.setAdjustTouch(getAdjustTouch().clone());
        }
        changedObject.setIsFresh(isFresh());
        changedObject.setOnMoveStatus(true);
        changedObject.getUserTransParas().set(getUserTransParas());
        changedObject.setUserTransParas();
        if (mObjectInfo.mSlotType == ObjectInfo.SLOT_TYPE_DESKTOP) {
            setBlendSortAxis(AXIS.Z);
        } else {
            setBlendSortAxis(AXIS.Y);
        }
    }

    public SEVector2i getAdjustTouch() {
        return mAdjustTouch;
    }

    public void setAdjustTouch(SEVector2i adjustTouch) {
        mAdjustTouch = adjustTouch;
    }

    public void setOnMoveStatus(boolean onFinger) {
        mOnFinger = onFinger;
    }

    public boolean isOnMove() {
        return mOnFinger;
    }

    public void setOnMove(boolean onFinger) {
        if (onFinger) {
            mSlotChanged = false;
            mPreObjectSlot = mObjectInfo.mObjectSlot.clone();
            mPreObjectSlot.mVesselID = mObjectInfo.mObjectSlot.mVesselID;
            if (getStartTranspara() != null) {
                SEVector3f userTranslate = getStartTranspara().mTranslate.clone();
                mAdjustTouch = getCamera().worldToScreenCoordinate(userTranslate);
                mAdjustTouch.selfSubtract(new SEVector2i(getTouchX(), getTouchY()));
            } else {
                mAdjustTouch = new SEVector2i();
            }
            mHomeScene.setStatus(HomeScene.STATUS_MOVE_OBJECT, true);
            getUserTransParas().set(getStartTranspara());
            if (!getRoot().equals(getParent())) {
                if (getParent() instanceof VesselObject) {
                    VesselObject vesselObject = (VesselObject) getParent();
                    mPreLayer = vesselObject.getVesselLayer();
                }
                changeParent(getRoot());
                setUserTransParas();
            }
            if (mObjectInfo.mSlotType == ObjectInfo.SLOT_TYPE_DESKTOP) {
                setBlendSortAxis(AXIS.Z);
            } else {
                setBlendSortAxis(AXIS.Y);
            }
            mHomeScene.getDragLayer().startDrag(this, getTouchX(), getTouchY());
        } else {
            mPreLayer = null;
            mHomeScene.setStatus(HomeScene.STATUS_MOVE_OBJECT, false);
            mHomeScene.getDragLayer().finishDrag();
        }
        mOnFinger = onFinger;
    }

    protected VesselObject getRoot() {
        if (mRoot == null) {
            mRoot = (VesselObject) getScene().getContentObject();
        }
        return mRoot;
    }

    public void onSlotSuccess() {

    }

    public void onSettingClick(){

    }

    public boolean resetPostion() {
        if (mPreLayer != null) {
            return mPreLayer.placeObjectToVesselDirectly(this);
        }
        return false;
    }

    public void updateComponentName(ComponentName name) {
        if (mObjectInfo != null) {
            mObjectInfo.updateComponentName(name);
        }
    }

    public void handOnClick() {
        if (isForAppEntry()) {
            getHomeScene().showAllApps();
            return;
        }

        if (getObjectInfo().mSlotType == ObjectInfo.SLOT_TYPE_DESKTOP ||
                ((!mHomeScene.getStatus(HomeScene.STATUS_ON_DESK_SIGHT)
                && !mHomeScene.getStatus(HomeScene.STATUS_ON_SKY_SIGHT)))) {
            if (!findAndStartIntent()) {
                if (HomeUtils.DEBUG)
                    Log.d(HomeUtils.TAG, "not find intent, show binding dialog");
                if (mObjectInfo.mIsNativeObject) {
                    getScene().handleMessage(HomeScene.MSG_TYPE_SHOW_BIND_APP_DIALOG, this);
                }
            }
        }
    }

    // for those object not allow to be change its bind and the intent is null,
    // e.g, decorator object, return true as it need not bind.
    public boolean findAndStartIntent() {
        Intent intent = mObjectInfo.getIntent();
        if (intent == null) {
            if (HomeUtils.DEBUG)
                Log.d("SEHome", "db does not have compoment");
            return !canChangeBind();
        }
        if (!HomeManager.getInstance().startActivity(intent)) {
            if (HomeUtils.DEBUG)
                Log.e("SEHome", "not found bind activity");
            return false;
        }
        return true;
    }

    public void handleNoMoreRoom() {
        setOnMove(false);
        NormalObject normalObject = getChangedToObj();
        if (normalObject != null) {
            normalObject.setChangedToObj(null);
            normalObject.getParent().removeChild(normalObject, true);
            setChangedToObj(null);
        }
    }

    public void handleOutsideRoom() {
        setOnMove(false);
        NormalObject normalObject = getChangedToObj();
        if (normalObject != null) {
            normalObject.setChangedToObj(null);
            normalObject.getParent().removeChild(normalObject, true);
            setChangedToObj(null);
        }
        getParent().removeChild(this, true);
        if (isFresh()) {
            setIsFresh(false);
            if (canUninstall()) {
                getScene().handleMessage(HomeScene.MSG_TYPE_SHOW_DELETE_OBJECTS, this.mName);
            } else {
                final int messageId = R.string.delete_preset_object_text;
                SESceneManager.getInstance().runInUIThread(new Runnable() {
                    public void run() {
                        Toast.makeText(HomeManager.getInstance().getContext(), messageId, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    public void handleSlotSuccess() {
        setOnMove(false);
        NormalObject changeToObject = getChangedToObj();
        if (changeToObject != null) {
            getObjectInfo().saveToDB();
            changeToObject.setChangedToObj(null);
            changeToObject.getParent().removeChild(changeToObject, true);
        } else if (getObjectInfo().mObjectSlot.mVesselID != -1) {
            // When the slot do not change, popup the menu dialog, when it
            // chagned,
            // update the DB info.
            if (mPreObjectSlot == null || !getObjectInfo().mObjectSlot.equals(mPreObjectSlot)
                    || getObjectInfo().mObjectSlot.mVesselID != mPreObjectSlot.mVesselID) {
                getObjectInfo().updateSlotDB();
            } else if (!mSlotChanged) {
                onPressButNotMove();
            }
        }
//        if (isFresh() && changeToObject == null) {
//            if (canChangeBind()) {
//                getScene().handleMessage(HomeScene.MSG_TYPE_SHOW_BIND_APP_DIALOG, this);
//            }
//        }
        onSlotSuccess();
        setChangedToObj(null);
        setIsFresh(false);
    }

    public void onPressButNotMove() {
        getScene().handleMessage(HomeScene.MSG_TYPE_SHOW_OBJECT_LONG_CLICK_DIALOG, this);
    }

    public void setObjectInfo(ObjectInfo info) {
        mObjectInfo = info;
        if (mObjectInfo.mModelInfo != null) {
            changeModelInfo(mObjectInfo.mModelInfo);
        }
    }

    public ObjectSlot getObjectSlot() {
        return mObjectInfo.mObjectSlot;
    }

    public ObjectInfo getObjectInfo() {
        return mObjectInfo;
    }

    public void setStartTranspara(SETransParas transParas) {
        mStartTranspara = transParas;
    }

    public SETransParas getStartTranspara() {
        return mStartTranspara;
    }

    public void updateIcon(Bitmap icon) {

    }

    public void resetIcon() {

    }

    public void updateLabel(String name) {

    }

    public final void startResize() {
        getHomeScene().startResize(this);
    }

    public void onSizeAndPositionChanged(Rect rect) {

    }

    public SEVector3f getSlotTranslateInVessel(VesselObject vessel, ObjectSlot slot) {
        SETransParas desTransParas = vessel.getTransParasInVessel(null, slot);
        SEVector3f translate = getDecoratorTranslate();
        if (null != desTransParas) {
            translate.selfAdd(desTransParas.mTranslate);
        }
        return translate;
    }

    protected void updateObjectSlotRect(Rect sizeRect) {
        getObjectSlot().mSpanX = sizeRect.width();
        getObjectSlot().mSpanY = sizeRect.height();
        getObjectSlot().mStartX = sizeRect.left;
        getObjectSlot().mStartY = sizeRect.top;
        getObjectInfo().updateSlotDB();
    }

    protected float getWallCellWidth(float spanX) {
        float gridSizeX = getHomeSceneInfo().mCellWidth + getHomeSceneInfo().mWidthGap;
        return spanX * gridSizeX - getHomeSceneInfo().mWidthGap;
    }

    protected float getWallCellHeight(float spanY) {
        float gridSizeY = getHomeSceneInfo().mCellHeight + getHomeSceneInfo().mHeightGap;
        return spanY * gridSizeY - getHomeSceneInfo().mHeightGap;
    }

    // this object is target to targetSlot, which is assure to be occupy by cmpObject,
    // detect if it is conflict between two objects or not.
//    public boolean isObjectSlotConflict(NormalObject cmpObject, ObjectSlot cmpSlot) {
//        ObjectSlot mySlot = getObjectSlot();
//        ObjectSlot objectSlot = cmpObject.getObjectSlot();
//        boolean xConflict = !((cmpSlot.mStartX <= objectSlot.mStartX && (cmpSlot.mStartX + cmpSlot.mSpanX) <= objectSlot.mStartX) || (cmpSlot.mStartX >= (objectSlot.mStartX + objectSlot.mSpanX) && (cmpSlot.mStartX + cmpSlot.mSpanX) >= (objectSlot.mStartX + objectSlot.mSpanX)));
//        boolean YConflict = !((cmpSlot.mStartY <= objectSlot.mStartY && (cmpSlot.mStartY + cmpSlot.mSpanY) <= objectSlot.mStartY) || (cmpSlot.mStartY >= (objectSlot.mStartY + objectSlot.mSpanY) && (cmpSlot.mStartY + cmpSlot.mSpanY) >= (objectSlot.mStartY + objectSlot.mSpanY)));
//        if (xConflict && YConflict) {
//            return true;
//        }
//        return false;
//    }

    /// object bounder volume size, for those specify in model config by minPoint and maxPoint,
    // it is read from model config, just as Desk does with its size, otherwise,
    // return {0, 0, 0} that require the calling method to calculate it via
    // spanX x spanY or something else as it expectation.
    protected SEVector.SEVector3f getObjectSize() {
        ObjectInfo objectInfo = getObjectInfo();
        if (null != objectInfo) {
            ModelInfo model = objectInfo.mModelInfo;
            if (null != model) {
                if (null != model.mMaxPoint && null != model.mMinPoint) {
                    return model.mMaxPoint.subtract(model.mMinPoint);
                }
            }
        }
        return SEVector3f.ZERO;
    }
    /// getObjectSize end

    /// change user trans paras according to its parent vessel, invocation happs
    /// after init or theme changed.
    private void changeTransParasInParent() {
        VesselObject vessel = getVesselParent();
        if (null != vessel) {
            vessel.changeObjectTransParasInVessel(this);
        }
    }

    protected static class SortByIndex implements Comparator<SEObject> {
        public int compare(SEObject lhs, SEObject rhs) {
            NormalObject child1 = (NormalObject) lhs;
            NormalObject child2 = (NormalObject) rhs;
            return child1.getObjectSlot().mSlotIndex - child2.getObjectSlot().mSlotIndex;
        }
    }

    protected static class SortByStartX implements Comparator<SEObject> {
        public int compare(SEObject lhs, SEObject rhs) {
            NormalObject child1 = (NormalObject) lhs;
            NormalObject child2 = (NormalObject) rhs;
            return child1.getObjectSlot().mStartX - child2.getObjectSlot().mStartX;
        }
    }

    /// cmpObject is trying to move to slot offset with moveX and moveY,
    // return true if this object has sensitive about such moving.
    public boolean isObjectSlotConflict(NormalObject cmpObject, int moveX, int moveY) {
        ObjectSlot cmpSlot = cmpObject.getObjectSlot().clone();
        cmpSlot.mStartX += moveX;
        cmpSlot.mStartY += moveY;
        return ObjectInfo.isObjectSlotConflict(this, cmpObject, cmpSlot);
    }

    protected float getExpectedNativeX() {
        ObjectSlot slot = getObjectSlot();
        return getWallCellWidth(slot.mSpanX);
    }
    protected float getExpectedNativeY() {
        ObjectSlot slot = getObjectSlot();
        return getWallCellHeight(slot.mSpanY);
    }

    public VesselObject getVesselParent() {
        SEObject parent = getParent();
        if (null != parent && parent instanceof VesselObject) {
            return (VesselObject)parent;
        }
        return null;
    }

    // default handler for long click on normal object, sub class
    // should override this method rather than set the other listener
    // within the initStatus.
    protected void onLongClick() {
        SETransParas startTranspara = new SETransParas();
        startTranspara.mTranslate = getAbsoluteTranslate();
        float angle = getUserRotate().getAngle();
        SEObject parent = getParent();
        while (parent != null) {
            angle = angle + parent.getUserRotate().getAngle();
            parent = parent.getParent();
        }
        startTranspara.mRotate.set(angle, 0, 0, 1);
        setStartTranspara(startTranspara);
        setOnMove(true);
    }

    /// moving associations begin
    public void setMovingAssociation(ArrayList<NormalObject> list) {
    }
    public ArrayList<NormalObject> getMovingAssociation() {
        return null;
    }
    public void releaseMovingAssociation(ObjectInfo.ObjectSlot placeSlot) {
    }
    /// moving associations end

    protected boolean isForAppEntry() {
        return ObjectInfo.PAD.equals(getObjectInfo().mType);
    }

    // objectMap, map the prefix of object name to feature key, and
    // featureMap, map the feature key to boolean value to enable or not
    protected void turnOnFeature(HashMap<String, String> objectMap, HashMap<String, Boolean> featureMap) {
        if (getObjectInfo().mIsNativeObject) {
            List<SEComponentAttribute> attributeList = getObjectInfo().mModelInfo.mComponentAttributes;
            if (null != attributeList) {
                Set<String> namePrefixSet = objectMap.keySet();
                for (String prefix : namePrefixSet) {
                    for (SEComponentAttribute attr : attributeList) {
                        if (null != attr.mRegularName &&
                                attr.mRegularName.startsWith(prefix)) {
                            String featureKey = objectMap.get(prefix);
                            Boolean enabled = featureMap.get(featureKey);
                            boolean on = null == enabled || enabled.booleanValue();
                            for (SEObject obj : mComponenetObjects) {
                                if (obj.getName().equals(attr.mComponentName)) {
                                    if (null != obj) {
                                        Log.v("NormalObject", "turnOnFeature, obj = " + obj.mName +
                                                ", feature = " + featureKey + ", value = " + on);
                                        obj.setVisible(on);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void broadcastObjectSlotChanged() {
        getHomeScene().onObjectSlotChanged(this);
    }

    public void placeInVesselSlot(int slotIndex) {
        getObjectSlot().mSlotIndex = slotIndex;
        handleSlotSuccess();
        broadcastObjectSlotChanged();
    }

    public void updateSlotInfo(ObjectSlot slot) {
        getObjectSlot().set(slot);
        getObjectInfo().updateSlotDB();
        broadcastObjectSlotChanged();
    }

    // called while a normal object was completely created and before adding into
    // scene, so there is an opportunity for convert it to the preferred type in scene,
    // e.g., app object and shortcut object might be convert to icon box.
    public NormalObject refineObject() {
        return this;
    }
    public SEVector3f getDecoratorTranslate() {
        return new SEVector3f(0f, 0f, 0f);
    }

    public void expandDecoratorToSlot() {
    }

    public boolean isAcceptedDecoratorSlot() {
        if (this instanceof AppObject ||
                getObjectInfo().mSlotType == ObjectInfo.SLOT_TYPE_DESKTOP) {
            return isAcceptedByDecorator();
        }

        return false;
    }

    // return true when it is 1x1 slot, which mean the object with the
    // slot could attach with a decorator
    private boolean isAcceptedByDecorator() {
        ObjectSlot slot = getObjectSlot();
        if (null != slot) {
            if (slot.mSpanY == 1 && slot.mSpanX == 1) {
                return true;
            }
        }
        return false;
    }

    private boolean mDecoratedLabel = true;
    protected final void onLabelShown(boolean showLabel) {
        if (showLabel != mDecoratedLabel) {
            mDecoratedLabel = showLabel;
            update(getHomeScene());
        }

    }
    protected final boolean isLabelShown() {
        return mDecoratedLabel;
    }

    private void setLabelVisible() {
        mDecoratedLabel = getHomeScene().getLabelShownPreference() <= 0;
    }
}

abstract class WallObject extends NormalObject implements House.WallRadiusChangedListener {
    public WallObject(HomeScene homeScene, String name, int index) {
        super(homeScene, name, index);
    }

    @Override
    public void initStatus() {
        super.initStatus();
        getHomeScene().addWallRadiusChangedListener(this);
    }

    @Override
    public void onRelease() {
        super.onRelease();
        getHomeScene().removeWallRadiusChangedListener(this);
    }
}
