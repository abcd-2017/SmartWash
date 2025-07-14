@ComponentV2
export struct LoadingFooter {
  @Param isLoading: boolean = false;

  build() {
    if (!!this.isLoading) {
      Row() {
        LoadingProgress().height(32).width(48)
        Text("加载中")
      }.width("100%")
      .height(64)
      .justifyContent(FlexAlign.Center)
    }
  }
}