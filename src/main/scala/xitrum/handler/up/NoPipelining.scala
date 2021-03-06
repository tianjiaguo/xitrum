package xitrum.handler.up

import io.netty.channel.{Channel, ChannelHandler, ChannelFuture, ChannelFutureListener, ChannelHandlerContext, MessageEvent, SimpleChannelUpstreamHandler}
import io.netty.handler.codec.http.{HttpHeaders, HttpRequest, HttpResponse}
import ChannelHandler.Sharable

object NoPipelining {
  def pauseReading(channel: Channel) {
    channel.setReadable(false)
  }

  // https://github.com/veebs/netty/commit/64f529945282e41eb475952fde382f234da8eec7
  def setResponseHeaderForKeepAliveRequest(request: HttpRequest, response: HttpResponse) {
    if (HttpHeaders.isKeepAlive(request))
      response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
  }

  def resumeReadingForKeepAliveRequestOrCloseOnComplete(
      request: HttpRequest,
      channel: Channel, channelFuture: ChannelFuture) {
    if (HttpHeaders.isKeepAlive(request)) {
      channelFuture.addListener(new ChannelFutureListener() {
        def operationComplete(future: ChannelFuture) {
          channel.setReadable(true)
        }
      })
    } else {
      channelFuture.addListener(ChannelFutureListener.CLOSE)
    }
  }

  // Combo of the above 2 methods
  def setResponseHeaderAndResumeReadingForKeepAliveRequestOrCloseOnComplete(
      request: HttpRequest, response: HttpResponse,
      channel: Channel, channelFuture: ChannelFuture) {
    if (HttpHeaders.isKeepAlive(request)) {
      response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
      channelFuture.addListener(new ChannelFutureListener() {
        def operationComplete(future: ChannelFuture) {
          channel.setReadable(true)
        }
      })
    } else {
      channelFuture.addListener(ChannelFutureListener.CLOSE)
    }
  }
}

@Sharable
/** http://mongrel2.org/static/book-finalch6.html */
class NoPipelining extends SimpleChannelUpstreamHandler with BadClientSilencer {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val channel = ctx.getChannel

    // Just in case more than one request has been read in
    // https://github.com/netty/netty/issues/214
    if (!channel.isReadable) {
      channel.close()
      return
    }

    NoPipelining.pauseReading(channel)
    ctx.sendUpstream(e)
  }
}
