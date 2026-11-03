import localUnitTest from './LocalUnit.test';
import liuYaoAnchorTest from './LiuYaoAnchor.test';
import meiHuaAnchorTest from './MeiHuaAnchor.test';
import qiMenAnchorTest from './QiMenAnchor.test';
import liuRenAnchorTest from './LiuRenAnchor.test';
import baZiAnchorTest from './BaZiAnchor.test';
import publicPathsTest from './PublicPaths.test';
import requestRulesTest from './RequestRules.test';
import httpUtilTest from './HttpUtil.test';

export default function testsuite() {
  localUnitTest();
  liuYaoAnchorTest();
  meiHuaAnchorTest();
  qiMenAnchorTest();
  liuRenAnchorTest();
  baZiAnchorTest();
  publicPathsTest();
  requestRulesTest();
  httpUtilTest();
}