import { promptAction } from "@kit.ArkUI";
import { resString } from '../utils/ResourceString'

export const checkStatus = (status: number) => {
  switch (status) {
    case 400:
      promptAction.showToast({ message: resString('check_status_1') })
      break;
    case 401:
      promptAction.showToast({ message: resString('check_status_2') })
      break;
    case 403:
      promptAction.showToast({ message: resString('check_status_3') })
      break;
    case 404:
      promptAction.showToast({ message: resString('check_status_4') })
      break;
    case 405:
      promptAction.showToast({ message: resString('check_status_5') })
      break;
    case 408:
      promptAction.showToast({ message: resString('check_status_6') })
      break;
    case 500:
      promptAction.showToast({ message: resString('check_status_7') })
      break;
    case 502:
      promptAction.showToast({ message: resString('check_status_8') })
      break;
    case 503:
      promptAction.showToast({ message: resString('check_status_9') })
      break;
    case 504:
      promptAction.showToast({ message: resString('check_status_10') })
      break;
    default:
      promptAction.showToast({ message: resString('check_status_11') })
  }
}