import { getCaptcha } from '../network/api/UserApi';
import { logger } from '../utils/Logger';
import { getValue, setValue } from '../utils/StoreageUtil';

@Entry
@Component
struct Index {
  @State message: string = 'Hello World';
  @State token: string = '2'
  @State phoneNumber: string = ''

  build() {
    Column() {
      Row() {
        TextInput({
          text: this.phoneNumber,
        }).contentType(ContentType.PHONE_NUMBER)
          .onChange(val => {
            this.phoneNumber = val
          })
      }
      .width("100%")
      .height(100)

      Row() {
        Button("获取").onClick(async () => {
          let res = await getCaptcha(this.phoneNumber)
          this.token = res.data ?? '1'
          logger.info("info", `${res.data}`)
        })
        Text(this.token)
      }.height(50)
      .width("100%").justifyContent(FlexAlign.SpaceAround)
      .alignItems(VerticalAlign.Center)
    }.height(400)
    .width("100%")
  }
}