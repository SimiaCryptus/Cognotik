package com.simiacryptus.cognotik.webui.servlet.action

    import com.simiacryptus.cognotik.fileserver.action.FsActionContext
    import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
    import com.simiacryptus.cognotik.platform.model.Session
    import com.simiacryptus.cognotik.platform.model.User
    import com.simiacryptus.cognotik.webui.application.getCookie
    import com.simiacryptus.cognotik.fileserver.handler.FsApiRoute
    import com.simiacryptus.cognotik.fileserver.handler.FsErrorCode
    import com.simiacryptus.cognotik.fileserver.handler.FsException
    import java.io.File

    /**
     * Root/user resolution for the *session-backed* mounts (`/fileIndex/<session>/...`), so the
     * ported actions can be installed on a multi-user app server. Mirrors
     * `SessionFileServlet.getFsApiRoot`: the first path segment is the session id, the user
     * comes from the auth cookie, and the working directory is that session's user dir.
     *
     * Local mounts (the CLI file server) pass their own fixed resolvers instead.
     */
    object SessionFsRoots {

      private val dataStorage by lazy { ApplicationServicesImpl.fileApplicationServices().dataStorageFactory }

      fun sessionOf(ctx: FsActionContext): Session {
        val raw = FsApiRoute.parse(ctx.req.pathInfo ?: ctx.req.servletPath)?.prefix
          ?: (ctx.req.pathInfo ?: ctx.req.servletPath ?: "/")
        val id = raw.split("/").firstOrNull { it.isNotBlank() }
          ?: throw FsException(FsErrorCode.EINVAL, "fsapi", null, "request is not scoped to a session")
        return Session(id)
      }

      fun userOf(ctx: FsActionContext): User {
        val session = sessionOf(ctx)
        return ApplicationServicesImpl.authenticationManager.getUser(ctx.req.getCookie())
          ?: throw FsException(
            FsErrorCode.EACCES, "fsapi", null,
            "not authenticated for session '${session.sessionId}'; log in and retry"
          )
      }

      fun rootOf(ctx: FsActionContext): File {
        val session = sessionOf(ctx)
        val user = ApplicationServicesImpl.authenticationManager.getUser(ctx.req.getCookie())
        if (user == null && !session.isGlobal()) {
          throw FsException(
            FsErrorCode.EACCES, "fsapi", null,
            "not authenticated for session '${session.sessionId}'; log in and retry"
          )
        }
        return dataStorage.getUserDir(user, session).apply { if (!exists()) mkdirs() }
      }
    }