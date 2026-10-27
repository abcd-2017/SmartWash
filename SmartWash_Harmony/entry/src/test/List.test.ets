import localUnitTest from './LocalUnit.test';
import liuYaoAnchorTest from './LiuYaoAnchor.test';
import meiHuaAnchorTest from './MeiHuaAnchor.test';
import qiMenAnchorTest from './QiMenAnchor.test';
import liuRenAnchorTest from './LiuRenAnchor.test';
import baZiAnchorTest from './BaZiAnchor.test';

export default function testsuite() {
  localUnitTest();
  liuYaoAnchorTest();
  meiHuaAnchorTest();
  qiMenAnchorTest();
  liuRenAnchorTest();
  baZiAnchorTest();
}