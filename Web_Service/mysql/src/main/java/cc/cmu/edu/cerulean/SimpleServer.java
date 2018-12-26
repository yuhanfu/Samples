package cc.cmu.edu.cerulean;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;

import javax.servlet.ServletException;

import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.deployment;
import static io.undertow.servlet.Servlets.servlet;
import io.undertow.Handlers;

import org.xnio.Options;

/**
 * Server class
 *
 */
public class SimpleServer {
    public static final String PATH = "/";

    public SimpleServer() throws Exception{

	}

    public static void main(final String[] args) {
        
		try {
			DeploymentInfo servletBuilder = deployment()
					.setClassLoader(SimpleServer.class.getClassLoader())
					.setContextPath(PATH)
					.setDeploymentName("handler.war")
					.addServlets(
							servlet("Backend", Backend.class)
							.addMapping("/q2")
					);

			DeploymentManager manager = defaultContainer().addDeployment(servletBuilder);
			manager.deploy();

			HttpHandler servletHandler = manager.start();
			PathHandler path = Handlers.path(Handlers.redirect(PATH))
					.addPrefixPath(PATH, servletHandler);

			Undertow server = Undertow.builder()
					.addHttpListener(8080, "0.0.0.0")
					.setHandler(path)
					.setWorkerOption(Options.WORKER_TASK_CORE_THREADS, 64)
					.setWorkerOption(Options.WORKER_TASK_MAX_THREADS, 128)
					.build();
			server.start();
		} catch (ServletException e) {
			throw new RuntimeException(e);
		}
    }
}
