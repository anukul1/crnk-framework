package io.crnk.client.internal;

import java.io.Serializable;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.crnk.client.CrnkClient;
import io.crnk.core.engine.document.Document;
import io.crnk.core.engine.document.Resource;
import io.crnk.core.engine.http.HttpMethod;
import io.crnk.core.engine.information.resource.ResourceField;
import io.crnk.core.engine.information.resource.ResourceInformation;
import io.crnk.core.engine.internal.document.mapper.DocumentMappingConfig;
import io.crnk.core.engine.internal.utils.ExceptionUtil;
import io.crnk.core.engine.internal.utils.JsonApiUrlBuilder;
import io.crnk.core.engine.query.QueryAdapter;
import io.crnk.core.queryspec.QuerySpec;
import io.crnk.core.queryspec.internal.QuerySpecAdapter;
import io.crnk.core.repository.ResourceRepositoryV2;
import io.crnk.core.repository.response.JsonApiResponse;
import io.crnk.core.resource.list.DefaultResourceList;

public class ResourceRepositoryStubImpl<T, I extends Serializable> extends ClientStubBase
		implements ResourceRepositoryV2<T, I> {

	protected ResourceInformation resourceInformation;


	public ResourceRepositoryStubImpl(CrnkClient client, Class<T> resourceClass, ResourceInformation resourceInformation,
			JsonApiUrlBuilder urlBuilder) {
		super(client, urlBuilder, resourceClass);
		this.resourceInformation = resourceInformation;
	}

	private Object executeUpdate(String requestUrl, T resource, boolean create) {
		JsonApiResponse response = new JsonApiResponse();
		response.setEntity(resource);

		ClientDocumentMapper documentMapper = client.getDocumentMapper();
		DocumentMappingConfig mappingConfig = new DocumentMappingConfig();
		QueryAdapter queryAdapter = new QuerySpecAdapter(null, client.getRegistry(), client.getQueryContext());
		final Document requestDocument = documentMapper.toDocument(response, queryAdapter, mappingConfig).get();

		final ObjectMapper objectMapper = client.getObjectMapper();
		String requestBodyValue = ExceptionUtil.wrapCatchedExceptions(new Callable<String>() {
			@Override
			public String call() throws Exception {
				return objectMapper.writeValueAsString(requestDocument);
			}
		});

		HttpMethod method = create || client.getPushAlways() ? HttpMethod.POST : HttpMethod.PATCH;

		return execute(requestUrl, ResponseType.RESOURCE, method, requestBodyValue);
	}


	@Override
	public <S extends T> S save(S entity) {
		return modify(entity, false);
	}

	@SuppressWarnings("unchecked")
	private <S extends T> S modify(S entity, boolean create) {
		Object id = getId(entity);

		if (create && !resourceInformation.isNested()) {
			id = null;
		}

		String url = urlBuilder.buildUrl(resourceInformation, id, (QuerySpec) null);
		if (create && resourceInformation.isNested() && !resourceInformation.isSingularNesting()) {
			// for multi-valued nested resource drop the nested id part
			url = url.substring(0, url.lastIndexOf('/'));
		}
		return (S) executeUpdate(url, entity, create);
	}

	@Override
	public <S extends T> S create(S entity) {
		return modify(entity, true);
	}

	private <S extends T> Object getId(S entity) {
		if (client.getPushAlways()) {
			return null;
		}
		else if (entity instanceof Resource) {
			return ((Resource) entity).getId();
		}
		else {
			ResourceField idField = resourceInformation.getIdField();
			return idField.getAccessor().getValue(entity);
		}
	}

	@Override
	public void delete(I id) {
		String url = urlBuilder.buildUrl(resourceInformation, id, (QuerySpec) null);
		executeDelete(url);
	}

	@Override
	public Class<T> getResourceClass() {
		return (Class<T>) resourceClass;
	}

	@Override
	public T findOne(I id, QuerySpec querySpec) {
		String url = urlBuilder.buildUrl(resourceInformation, id, querySpec);
		return findOne(url);
	}

	@Override
	public DefaultResourceList<T> findAll(QuerySpec querySpec) {
		String url = urlBuilder.buildUrl(resourceInformation, null, querySpec);
		return findAll(url);
	}

	@Override
	public DefaultResourceList<T> findAll(Iterable<I> ids, QuerySpec queryPaquerySpecrams) {
		String url = urlBuilder.buildUrl(resourceInformation, ids, queryPaquerySpecrams);
		return findAll(url);
	}

	@SuppressWarnings("unchecked")
	public DefaultResourceList<T> findAll(String url) {
		return (DefaultResourceList<T>) executeGet(url, ResponseType.RESOURCES);
	}

	@SuppressWarnings("unchecked")
	protected T findOne(String url) {
		return (T) executeGet(url, ResponseType.RESOURCE);
	}

}
