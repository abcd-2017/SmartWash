@Component
export struct ItemTitle {
  @Prop title: string

  build() {
    Column() {
      Text(this.title)
        .fontWeight(FontWeight.Bold)
    }.alignItems(HorizontalAlign.Start)
    .width("100%")
  }
}